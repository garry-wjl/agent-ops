package ink.garry.rd.agent.ws.infra.skill.agentscope;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.SkillResourceCodec;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillResourceFileMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillVersionMapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AgentScope {@link AgentSkillRepository} 适配器：把 agentscope 框架的 "按 name 取 skill" 接口
 * 接到 rd-agent-be 业务侧的 Skill / SkillVersion + <b>skill_resource_file</b> 表（v3.0 起资源全部入库），
 * 并以 {@link SkillVersionEntity#getVersion} 为版本维度提供历史版本读取能力。
 *
 * <h3>v3.0 变更：去对象存储</h3>
 * 旧实现从 OSS 拉整个 zip 再内存解压切分；v3.0 资源文件树已入库（{@code skill_resource_file}，
 * owner_type=VERSION），本适配器改为<b>直接按版本 num 读 DB 资源树</b>装配 AgentSkill —— 不再依赖
 * {@code OssClient} / HTTP 下载 / zip 解压。文本节点直接取 content，二进制节点经
 * {@link SkillResourceCodec#toAgentResourceValue} 还原为 SkillBox 物化约定的 {@code "base64:"} 前缀形态。
 *
 * <h3>语义约定（不变）</h3>
 * <ul>
 *   <li><b>name 参数即 skillNum</b>：agentscope 接口的 {@code name} 解释为 rd-agent 的 {@code skillNum}。</li>
 *   <li><b>只读</b>：{@link #save} / {@link #delete} 抛 {@link UnsupportedOperationException}，
 *       写路径必须经 application 层 {@code SkillCommandService}。</li>
 *   <li><b>默认取当前发布版本</b>：{@link #getSkill} 取 {@code currentVersionNum}；
 *       {@link #getSkillByVersion} 支持任意历史版本。</li>
 *   <li><b>仅暴露 PUBLISHED Skill</b>：列表 / 存在性判断过滤 {@code status = PUBLISHED}。</li>
 * </ul>
 *
 * <h3>依赖注入边界</h3>
 * 仅注入 skill 聚合自身的 {@link SkillMapper} / {@link SkillVersionMapper} /
 * {@link SkillResourceFileMapper}（同聚合子表）；不引 Gateway / DomainEventPublisher / 其他聚合 Repository。
 */
@Slf4j
@Component
public class AgentScopeSkillRepositoryAdapter implements AgentSkillRepository {

    /** 适配器来源标识。 */
    private static final String SOURCE = "rd-agent";

    /** 仓库类型标识（v3.0：去 OSS，资源入库）。 */
    private static final String REPOSITORY_TYPE = "rd-agent-mysql";

    /** 仓库位置标识。 */
    private static final String REPOSITORY_LOCATION = "rd_agent.skill + skill_version + skill_resource_file";

    /** {@link SkillStatus#PUBLISHED} 的字符串字面量，与 SkillEntity.status 列（VARCHAR）直接比较。 */
    private static final String STATUS_PUBLISHED = SkillStatus.PUBLISHED.name();

    /** 业务异常 code：资源不存在。与 client.common.BizCode#NOT_FOUND 数值一致；infra 不依赖 client。 */
    private static final int CODE_NOT_FOUND = 1006;

    /** Skill 聚合主表 Mapper。 */
    @Resource
    private SkillMapper skillMapper;

    /** SkillVersion 持久化 Mapper。 */
    @Resource
    private SkillVersionMapper skillVersionMapper;

    /** Skill 资源文件树 Mapper（v3.0：替代 OssClient 读资源）。 */
    @Resource
    private SkillResourceFileMapper skillResourceFileMapper;

    /**
     * 按 skillNum 取当前发布版本的 AgentSkill。
     *
     * @param skillNum Skill 业务编号（前缀 SKL）
     * @return AgentSkill；Skill 不存在 / 无 currentVersionNum 时返回 {@code null}
     * @throws BusinessException 装配失败时（code {@value #CODE_NOT_FOUND}）
     */
    @Override
    public AgentSkill getSkill(String skillNum) {
        if (StrUtil.isBlank(skillNum)) {
            return null;
        }
        SkillEntity skillEntity = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, skillNum));
        if (skillEntity == null) {
            return null;
        }
        String currentVersion = skillEntity.getCurrentVersionNum();
        if (StrUtil.isBlank(currentVersion)) {
            log.warn("Skill {} has no currentVersionNum, cannot expose to agentscope", skillNum);
            return null;
        }
        return loadVersionAsAgentSkill(skillEntity, currentVersion);
    }

    /**
     * 按 skillNum + version 取指定历史版本的 AgentSkill（扩展方法，不属 {@link AgentSkillRepository} 契约）。
     *
     * @param skillNum Skill 业务编号
     * @param version  目标版本号
     * @return AgentSkill；任一查不到时返回 {@code null}
     */
    public AgentSkill getSkillByVersion(String skillNum, String version) {
        if (StrUtil.isBlank(skillNum) || StrUtil.isBlank(version)) {
            return null;
        }
        SkillEntity skillEntity = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, skillNum));
        if (skillEntity == null) {
            return null;
        }
        return loadVersionAsAgentSkill(skillEntity, version);
    }

    /**
     * 列出所有 PUBLISHED Skill 的 skillNum。
     *
     * @return PUBLISHED 状态的 skillNum 列表，按 num 升序
     */
    @Override
    public List<String> getAllSkillNames() {
        List<SkillEntity> entities = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getStatus, STATUS_PUBLISHED)
                .select(SkillEntity::getNum)
                .orderByAsc(SkillEntity::getNum));
        return entities.stream().map(SkillEntity::getNum).collect(Collectors.toList());
    }

    /**
     * 全量装载所有 PUBLISHED Skill（按 currentVersionNum 取版本快照 + 读 DB 资源树）。
     *
     * @return 成功装载的 AgentSkill 列表；失败条目跳过并 WARN
     */
    @Override
    public List<AgentSkill> getAllSkills() {
        List<SkillEntity> entities = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getStatus, STATUS_PUBLISHED));
        return entities.stream()
                .map(this::tryLoadCurrent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 适配器只读 — 写入禁止，强制走 application 层 {@code SkillCommandService}。
     *
     * @param skills 忽略
     * @param force  忽略
     * @return 永不返回
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        throw new UnsupportedOperationException(
                "AgentScopeSkillRepositoryAdapter is read-only. Use SkillCommandService.publish() to write skills.");
    }

    /**
     * 适配器只读 — 删除禁止，强制走 application 层 {@code SkillCommandService}。
     *
     * @param skillNum 忽略
     * @return 永不返回
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public boolean delete(String skillNum) {
        throw new UnsupportedOperationException(
                "AgentScopeSkillRepositoryAdapter is read-only. Use SkillCommandService.delete() to remove skills.");
    }

    /**
     * 按 skillNum 判断是否存在 PUBLISHED Skill。
     *
     * @param skillNum Skill 业务编号
     * @return 当且仅当存在该 skillNum 且 {@code status = PUBLISHED} 时返回 true
     */
    @Override
    public boolean skillExists(String skillNum) {
        if (StrUtil.isBlank(skillNum)) {
            return false;
        }
        Long count = skillMapper.selectCount(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, skillNum)
                .eq(SkillEntity::getStatus, STATUS_PUBLISHED));
        return count != null && count > 0;
    }

    /**
     * 仓库元信息描述。
     *
     * @return type = {@value #REPOSITORY_TYPE}，writable = false
     */
    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo(REPOSITORY_TYPE, REPOSITORY_LOCATION, /*writable=*/ false);
    }

    /**
     * 仓库来源标识。
     *
     * @return {@value #SOURCE}
     */
    @Override
    public String getSource() {
        return SOURCE;
    }

    /**
     * 始终只读；setter 静默忽略以防外部误调改变契约。
     *
     * @param writeable 忽略；传 true 时打 WARN 日志
     */
    @Override
    public void setWriteable(boolean writeable) {
        if (writeable) {
            log.warn("Attempt to enable write on AgentScopeSkillRepositoryAdapter ignored; adapter is read-only by design.");
        }
    }

    /**
     * @return 始终 false，见类注释 "只读" 约定
     */
    @Override
    public boolean isWriteable() {
        return false;
    }

    /**
     * 关闭钩子。当前实现无需释放任何资源（Mapper 都是 Spring 管理的单例）。
     */
    @Override
    public void close() {
        log.info("AgentScopeSkillRepositoryAdapter closed (no-op, beans owned by Spring).");
    }

    // ---------------- 私有辅助 ----------------

    /**
     * {@link #getAllSkills} 内部：为单个 SkillEntity 尝试装载当前版本的 AgentSkill；
     * 任何异常都捕获并降级为 null。
     *
     * @param entity 已查到的 Skill 主表行
     * @return AgentSkill；装载失败返回 null
     */
    private AgentSkill tryLoadCurrent(SkillEntity entity) {
        String currentVersion = entity.getCurrentVersionNum();
        if (StrUtil.isBlank(currentVersion)) {
            log.warn("Skip skill {} in getAllSkills: currentVersionNum is blank", entity.getNum());
            return null;
        }
        try {
            return loadVersionAsAgentSkill(entity, currentVersion);
        } catch (RuntimeException ex) {
            log.warn("Failed to load AgentSkill for {} (version={}), skipped: {}",
                    entity.getNum(), currentVersion, ex.getMessage());
            return null;
        }
    }

    /**
     * 按 Skill + 指定 version 加载并装配 AgentSkill（v3.0：从 DB 资源树读取）。
     * <p>
     * 名称 / 描述等对外字段取自 SkillVersion 快照；SKILL.md 文本与资源 Map 从
     * {@code skill_resource_file}（owner_type=VERSION，owner_num=版本 num）读取并还原。
     *
     * @param skillEntity 已查到的 Skill 主表行（非 null）
     * @param version     目标版本号（非空）
     * @return AgentSkill；SkillVersion 不存在 / 已软删时返回 null
     * @throws BusinessException 资源树缺 SKILL.md 时（code {@value #CODE_NOT_FOUND}）
     */
    private AgentSkill loadVersionAsAgentSkill(SkillEntity skillEntity, String version) {
        Assert.notNull(skillEntity, "skillEntity must not be null");
        Assert.notBlank(version, "version must not be blank");

        SkillVersionEntity versionEntity = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillNum, skillEntity.getNum())
                .eq(SkillVersionEntity::getVersion, version));
        if (versionEntity == null) {
            log.warn("SkillVersion not found: skillNum={}, version={}", skillEntity.getNum(), version);
            return null;
        }

        // 读版本快照资源树（owner_type=VERSION）
        List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                new LambdaQueryWrapper<SkillResourceFileEntity>()
                        .eq(SkillResourceFileEntity::getOwnerType, SkillResourceFileEntity.OWNER_TYPE_VERSION)
                        .eq(SkillResourceFileEntity::getOwnerNum, versionEntity.getNum())
                        .orderByAsc(SkillResourceFileEntity::getPath));

        String skillContent = null;
        Map<String, String> resources = new HashMap<>();
        for (SkillResourceFileEntity row : rows) {
            if (!SkillResourceFileType.FILE.name().equals(row.getType())) {
                continue;
            }
            if (SkillResourceCodec.SKILL_MD_FILENAME.equals(row.getPath())) {
                skillContent = row.getContent();
                continue;
            }
            // 还原为 SkillBox 物化约定：文本直取、二进制加 base64: 前缀
            resources.put(row.getPath(), SkillResourceCodec.toAgentResourceValue(
                    SkillResourceFileEntity.toValueObject(row)));
        }
        if (skillContent == null) {
            throw new BusinessException(CODE_NOT_FOUND,
                    "skill 资源树未找到 SKILL.md skillNum=" + skillEntity.getNum() + " version=" + version);
        }

        return AgentSkill.builder()
                .name(versionEntity.getName())
                .description(versionEntity.getDescription())
                .skillContent(skillContent)
                .resources(resources)
                .source(SOURCE + "@" + versionEntity.getVersion())
                .putMetadata("skillNum", skillEntity.getNum())
                .putMetadata("version", versionEntity.getVersion())
                .putMetadata("ownerUserId", skillEntity.getOwnerUserId())
                .putMetadata("skillSource", skillEntity.getSource())
                .putMetadata("status", versionEntity.getStatus())
                .build();
    }
}
