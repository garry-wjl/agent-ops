package ink.garry.rd.agent.ws.application.skill;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckErrorDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckRecordDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckRecordPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDetailDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceFileDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceTreeDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillVersionDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillVersionDetailDTO;
import ink.garry.rd.agent.ws.client.skill.dto.VersionDiffDTO;
import ink.garry.rd.agent.ws.client.skill.vo.SkillBindableVersionVO;
import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.SkillVersion;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.common.util.SkillResourceCodec;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillResourceFileMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillVersionMapper;
import ink.garry.rd.agent.ws.infra.skillcheck.entity.SkillCheckRecordEntity;
import ink.garry.rd.agent.ws.infra.skillcheck.mapper.SkillCheckRecordMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Skill 读侧应用服务（v2.10）。
 * <p>
 * 全部读查询走 MyBatis-Plus {@link LambdaQueryWrapper} 构造条件 + {@code BaseMapper}
 * 的 selectOne / selectList / selectPage / selectCount 四件套，<b>不写自定义 SQL</b>
 * （Mapper 仅声明 {@code extends BaseMapper<Entity>}）。所有方法返回 client.skill.dto
 * 下的 DTO，禁止把 Entity 暴露到 Service 边界之外。
 *
 * <h3>方法清单</h3>
 * <ul>
 *   <li>读检查类：{@link #existsByWorkspaceAndName} / {@link #existsByVersion}</li>
 *   <li>列表 / 详情：{@link #pageList} / {@link #detail} / {@link #versionList}
 *       / {@link #versionDetail} / {@link #compareVersions}</li>
 * </ul>
 */
@Slf4j
@Service
public class SkillQueryService {

    @Resource
    private SkillMapper skillMapper;
    @Resource
    private SkillVersionMapper skillVersionMapper;
    @Resource
    private SkillResourceFileMapper skillResourceFileMapper;
    @Resource
    private SkillCheckRecordMapper skillCheckRecordMapper;

    // ============================================================
    // 存在性查询（v2.10：CQRS 约束 —— Command 不直接调 Mapper，
    // 所有读查询统一走本 Service）
    // ============================================================

    /**
     * 判断指定工作空间下是否已存在同名 Skill（仅 SELF 来源参与；用于 createSkill 唯一性预检）。
     *
     * @param workspaceNum 工作空间业务编号（不含兜底默认值）
     * @param name         Skill 名称
     * @return 存在返回 true，否则 false
     */
    public boolean existsByWorkspaceAndName(String workspaceNum, String name) {
        Assert.notBlank(name, "name 不能为空");
        Long count = skillMapper.selectCount(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getWorkspaceNum, workspaceNum)
                .eq(SkillEntity::getName, name)
                .eq(SkillEntity::getSource, SkillSource.SELF.name()));
        return count != null && count > 0;
    }

    /**
     * 判断同一 Skill 下指定版本号是否已存在（用于 publish 唯一性预检；rollbackToVersion 存在性预检）。
     *
     * @param skillNum Skill 业务编号
     * @param version  版本号字符串
     * @return 存在返回 true，否则 false
     */
    public boolean existsByVersion(String skillNum, String version) {
        Assert.notBlank(skillNum, "skillNum 不能为空");
        Assert.notBlank(version, "version 不能为空");
        Long count = skillVersionMapper.selectCount(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillNum, skillNum)
                .eq(SkillVersionEntity::getVersion, version));
        return count != null && count > 0;
    }

    /**
     * 按 (skillNum, version) 查 SkillVersion 的业务编号 num（CQRS 约束：Command 通过本 helper 拿
     * num 后再 {@code factory.buildSkillVersionByNum} 加载领域对象）。
     *
     * @param skillNum Skill 业务编号
     * @param version  版本号字符串
     * @return 存在返回 num；不存在返回 null
     */
    public String findVersionNum(String skillNum, String version) {
        Assert.notBlank(skillNum, "skillNum 不能为空");
        Assert.notBlank(version, "version 不能为空");
        SkillVersionEntity e = skillVersionMapper.selectOne(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillNum, skillNum)
                .eq(SkillVersionEntity::getVersion, version));
        return e == null ? null : e.getNum();
    }

    // ============================================================
    // pageList
    // ============================================================

    /**
     * 分页查询 Skill 列表（按 source / status / keyword / ownerUserId 筛选，按 update_time DESC）。
     *
     * @param param 筛选条件
     * @return 分页结果，元素为 SkillDTO
     */
    public PageVO<SkillDTO> pageList(SkillPageQueryParamDTO param, String workspaceNum) {
        Assert.notNull(param, "查询参数不能为空");
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = (param.getPageSize() == null || param.getPageSize() < 1) ? 20 : param.getPageSize();

        // 工作空间条件过滤：由 Controller 经 BaseController 取得后传入
        LambdaQueryWrapper<SkillEntity> wrapper = Wrappers.<SkillEntity>lambdaQuery()
                .eq(StrUtil.isNotBlank(workspaceNum), SkillEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(param.getSource()), SkillEntity::getSource, param.getSource())
                .eq(StrUtil.isNotBlank(param.getStatus()), SkillEntity::getStatus, param.getStatus())
                .eq(StrUtil.isNotBlank(param.getOwnerUserId()),
                        SkillEntity::getOwnerUserId, param.getOwnerUserId())
                // keyword 在 name / description 内 OR LIKE 匹配
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(SkillEntity::getName, param.getKeyword())
                        .or()
                        .like(SkillEntity::getDescription, param.getKeyword()))
                .orderByDesc(SkillEntity::getUpdateTime);

        Page<SkillEntity> page = new Page<>(pageNo, pageSize);
        IPage<SkillEntity> result = skillMapper.selectPage(page, wrapper);

        List<SkillDTO> items = result.getRecords().stream()
                .map(SkillEntity::toDomain)
                .map(SkillQueryService::skillToDTO)
                .collect(Collectors.toList());
        return PageVO.of(items, result.getTotal(), pageNo, pageSize);
    }

    // ============================================================
    // detail
    // ============================================================

    /**
     * 加载 Skill 详情：含基础字段 + 当前在线版本嵌套
     * （PUBLISHED/DEPRECATED 时有；DRAFT 或 currentVersionNum 为空时为 null）。
     *
     * @param num Skill 业务编号
     * @return 详情 DTO；Skill 不存在抛 {@link BusinessException}(3003)
     */
    public SkillDetailDTO detail(String num, String workspaceNum) {
        Assert.notBlank(num, "Skill 业务编号不能为空");

        SkillEntity entity = skillMapper.selectOne(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(3003, "Skill 不存在 num=" + num);
        }
        // 跨空间访问拦截：传入空间编号且与资源归属不一致时拒绝（1003 = FORBIDDEN）
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(1003, "无权访问该空间的 Skill");
        }
        SkillDTO base = skillToDTO(SkillEntity.toDomain(entity));

        SkillVersionDTO currentVersion = null;
        if (StrUtil.isNotBlank(entity.getCurrentVersionNum())) {
            SkillVersionEntity verEntity = skillVersionMapper.selectOne(
                    Wrappers.<SkillVersionEntity>lambdaQuery()
                            .eq(SkillVersionEntity::getSkillNum, num)
                            .eq(SkillVersionEntity::getVersion, entity.getCurrentVersionNum()));
            if (verEntity != null) {
                currentVersion = versionToDTO(SkillVersionEntity.toDomain(verEntity));
            }
        }

        SkillDetailDTO detailDTO = SkillDetailDTO.builder()
                .skill(base)
                .currentVersion(currentVersion)
                // reuseCount：M3 接入 Agent 复用统计后填充
                .reuseCount(0)
                .build();

        // v3.0 hotfix：从版本快照资源树提取 SKILL.md 正文，供详情页「基本信息」Tab 展示
        if (StrUtil.isNotBlank(entity.getCurrentVersionNum())) {
            String versionNum = findVersionNum(num, entity.getCurrentVersionNum());
            if (versionNum != null) {
                detailDTO.setSkillMdContent(loadSkillMdFromResourceTree(versionNum));
            }
        }

        return detailDTO;
    }

    // ============================================================
    // versionList
    // ============================================================

    /**
     * 列出 Skill 版本历史（按 create_time DESC）。
     */
    public List<SkillVersionDTO> versionList(String skillNum) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        List<SkillVersionEntity> entities = skillVersionMapper.selectList(
                Wrappers.<SkillVersionEntity>lambdaQuery()
                        .eq(SkillVersionEntity::getSkillNum, skillNum)
                        .orderByDesc(SkillVersionEntity::getCreateTime));
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        return entities.stream()
                .map(SkillVersionEntity::toDomain)
                .map(SkillQueryService::versionToDTO)
                .collect(Collectors.toList());
    }

    // ============================================================
    // versionDetail
    // ============================================================

    /**
     * 单版本详情。
     *
     * @param skillNum Skill 业务编号
     * @param version  版本号字符串
     * @return 单版本详情；不存在抛 {@link BusinessException}(3003)
     */
    public SkillVersionDetailDTO versionDetail(String skillNum, String version) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(version, "版本号不能为空");

        SkillVersionEntity entity = loadVersionEntity(skillNum, version);
        if (entity == null) {
            throw new BusinessException(3003,
                    "SkillVersion 不存在 skillNum=" + skillNum + " version=" + version);
        }
        return SkillVersionDetailDTO.builder()
                .version(versionToDTO(SkillVersionEntity.toDomain(entity)))
                .build();
    }

    // ============================================================
    // compareVersions
    // ============================================================

    /**
     * 版本对比（v2.10：仅字段级 diff —— name / description / tags；
     * SKILL.md 行级 diff 待 SkillFileStorage 接入后再补 mdDiff 字段）。
     */
    public VersionDiffDTO compareVersions(String skillNum, String versionA, String versionB) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(versionA, "versionA 不能为空");
        Assert.notBlank(versionB, "versionB 不能为空");

        SkillVersionEntity entityA = loadVersionEntity(skillNum, versionA);
        SkillVersionEntity entityB = loadVersionEntity(skillNum, versionB);
        if (entityA == null || entityB == null) {
            throw new BusinessException(3003,
                    "compareVersions 目标版本不存在 skillNum=" + skillNum
                            + " vA=" + versionA + " vB=" + versionB);
        }
        SkillVersion a = SkillVersionEntity.toDomain(entityA);
        SkillVersion b = SkillVersionEntity.toDomain(entityB);

        return VersionDiffDTO.builder()
                .versionA(versionA)
                .versionB(versionB)
                .nameDiff(Objects.equals(a.getName(), b.getName())
                        ? null : a.getName() + " → " + b.getName())
                .descriptionDiff(Objects.equals(a.getDescription(), b.getDescription())
                        ? null : a.getDescription() + " → " + b.getDescription())
                .tagsDiff(diffTags(a.getTags(), b.getTags()))
                .build();
    }

    // ============================================================
    // resourceTree（v3.0）
    // ============================================================

    /**
     * 加载 Skill 资源文件树（v3.0）。
     * <p>version 为空 → 取草稿树（owner_type=SKILL，owner_num=skillNum）；
     * version 非空 → 取版本快照树（owner_type=VERSION，owner_num=该版本 num）。整树含内容（图片 Base64 随树）。
     *
     * @param skillNum Skill 业务编号
     * @param version  版本号；为空取草稿树
     * @return 资源文件树 DTO
     */
    public SkillResourceTreeDTO resourceTree(String skillNum, String version) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");

        String ownerType;
        String ownerNum;
        if (StrUtil.isBlank(version)) {
            ownerType = SkillResourceFileEntity.OWNER_TYPE_SKILL;
            ownerNum = skillNum;
        } else {
            String versionNum = findVersionNum(skillNum, version);
            if (versionNum == null) {
                throw new BusinessException(1004,
                        "SkillVersion 不存在 skillNum=" + skillNum + " version=" + version);
            }
            ownerType = SkillResourceFileEntity.OWNER_TYPE_VERSION;
            ownerNum = versionNum;
        }

        List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                Wrappers.<SkillResourceFileEntity>lambdaQuery()
                        .eq(SkillResourceFileEntity::getOwnerType, ownerType)
                        .eq(SkillResourceFileEntity::getOwnerNum, ownerNum)
                        .orderByAsc(SkillResourceFileEntity::getPath));

        List<SkillResourceFileDTO> files = rows.stream()
                .map(SkillQueryService::resourceEntityToDTO)
                .collect(Collectors.toList());
        return SkillResourceTreeDTO.builder()
                .skillNum(skillNum)
                .version(version)
                .files(files)
                .build();
    }

    // ============================================================
    // checkRecordPage / checkRecordDetail（v3.0）
    // ============================================================

    /**
     * 分页查询 Skill 发布检测记录（按 create_time DESC）。
     *
     * @param param 查询参数；skillNum 必填
     * @return 分页结果，元素为 SkillCheckRecordDTO
     */
    public PageVO<SkillCheckRecordDTO> checkRecordPage(SkillCheckRecordPageQueryParamDTO param) {
        Assert.notNull(param, "查询参数不能为空");
        Assert.notBlank(param.getSkillNum(), "skillNum 不能为空");
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = (param.getPageSize() == null || param.getPageSize() < 1) ? 20 : param.getPageSize();

        Page<SkillCheckRecordEntity> page = new Page<>(pageNo, pageSize);
        IPage<SkillCheckRecordEntity> result = skillCheckRecordMapper.selectPage(page,
                Wrappers.<SkillCheckRecordEntity>lambdaQuery()
                        .eq(SkillCheckRecordEntity::getSkillNum, param.getSkillNum())
                        .orderByDesc(SkillCheckRecordEntity::getCreateTime));

        List<SkillCheckRecordDTO> items = result.getRecords().stream()
                .map(SkillQueryService::checkRecordEntityToDTO)
                .collect(Collectors.toList());
        return PageVO.of(items, result.getTotal(), pageNo, pageSize);
    }

    /**
     * 单条检测记录详情。
     *
     * @param recordNum 检测记录业务编号（SCR...）
     * @return 检测记录详情 DTO；不存在抛 {@link BusinessException}
     */
    public SkillCheckRecordDTO checkRecordDetail(String recordNum) {
        Assert.notBlank(recordNum, "检测记录编号不能为空");
        SkillCheckRecordEntity entity = skillCheckRecordMapper.selectOne(
                Wrappers.<SkillCheckRecordEntity>lambdaQuery()
                        .eq(SkillCheckRecordEntity::getNum, recordNum));
        if (entity == null) {
            throw new BusinessException(1004, "检测记录不存在 num=" + recordNum);
        }
        return checkRecordEntityToDTO(entity);
    }

    // ============================================================
    // bindableVersions（Agent 绑定 Skill 版本：可绑定版本列表）
    // ============================================================

    /**
     * 列出某 Skill 的可绑定版本（仅 {@code PUBLISHED}，按 create_time DESC）。
     * <p>
     * 供 Agent 配置页 Skill 版本选择器使用：DRAFT / DEPRECATED 版本不可绑定，不在返回之列；
     * {@code latest} 标记等于 {@code Skill.currentVersionNum} 的那一版（即最新发布版），
     * 供前端默认选中与「有新版本」提示比对。
     *
     * @param skillNum Skill 业务编号
     * @return 可绑定版本列表（可空但非 null）；Skill 不存在抛 {@link BusinessException}(3003)
     */
    public List<SkillBindableVersionVO> bindableVersions(String skillNum) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        SkillEntity skill = skillMapper.selectOne(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getNum, skillNum));
        if (skill == null) {
            throw new BusinessException(3003, "Skill 不存在 num=" + skillNum);
        }
        String latestVersion = skill.getCurrentVersionNum();
        List<SkillVersionEntity> entities = skillVersionMapper.selectList(
                Wrappers.<SkillVersionEntity>lambdaQuery()
                        .eq(SkillVersionEntity::getSkillNum, skillNum)
                        .eq(SkillVersionEntity::getStatus, SkillStatus.PUBLISHED.name())
                        .orderByDesc(SkillVersionEntity::getCreateTime));
        return entities.stream()
                .map(e -> SkillBindableVersionVO.builder()
                        .versionNum(e.getVersion())
                        .publishedTime(e.getCreateTime())
                        .latest(StrUtil.isNotBlank(latestVersion) && latestVersion.equals(e.getVersion()))
                        .build())
                .collect(Collectors.toList());
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 按 (skillNum, version) 定位单行 SkillVersionEntity；versionDetail 与 compareVersions 共用。
     * <p>MyBatis-Plus 全局 logic-delete 配置自动追加 {@code WHERE deleted=0}，wrapper 不写该条件。
     */
    private SkillVersionEntity loadVersionEntity(String skillNum, String version) {
        return skillVersionMapper.selectOne(Wrappers.<SkillVersionEntity>lambdaQuery()
                .eq(SkillVersionEntity::getSkillNum, skillNum)
                .eq(SkillVersionEntity::getVersion, version));
    }

    /** Skill 领域对象 → SkillDTO。 */
    private static SkillDTO skillToDTO(Skill skill) {
        return SkillDTO.builder()
                .num(skill.getNum())
                .name(skill.getName())
                .description(skill.getDescription())
                .tags(skill.getTags())
                .source(skill.getSource() == null ? null : skill.getSource().name())
                .ownerUserId(skill.getOwnerUserId())
                .status(skill.getStatus() == null ? null : skill.getStatus().name())
                .currentVersionNum(skill.getCurrentVersionNum())
                .createTime(skill.getCreateTime())
                .updateTime(skill.getUpdateTime())
                .build();
    }

    /** SkillVersion 领域对象 → SkillVersionDTO。 */
    private static SkillVersionDTO versionToDTO(SkillVersion v) {
        return SkillVersionDTO.builder()
                .num(v.getNum())
                .skillNum(v.getSkillNum())
                .version(v.getVersion())
                .name(v.getName())
                .description(v.getDescription())
                .tags(v.getTags())
                .status(v.getStatus() == null ? null : v.getStatus().name())
                .createTime(v.getCreateTime())
                .build();
    }

    /** 计算 tags 集合差集。 */
    private static VersionDiffDTO.TagsDiff diffTags(List<String> a, List<String> b) {
        Set<String> setA = a == null ? new HashSet<>() : new HashSet<>(a);
        Set<String> setB = b == null ? new HashSet<>() : new HashSet<>(b);
        List<String> onlyInA = setA.stream().filter(t -> !setB.contains(t)).sorted().collect(Collectors.toList());
        List<String> onlyInB = setB.stream().filter(t -> !setA.contains(t)).sorted().collect(Collectors.toList());
        List<String> common = setA.stream().filter(setB::contains).sorted().collect(Collectors.toList());
        return VersionDiffDTO.TagsDiff.builder()
                .onlyInA(onlyInA)
                .onlyInB(onlyInB)
                .common(common)
                .build();
    }

    /** Skill 资源文件 Entity → DTO（v3.0）。 */
    private static SkillResourceFileDTO resourceEntityToDTO(SkillResourceFileEntity e) {
        int slash = e.getPath() == null ? -1 : e.getPath().lastIndexOf('/');
        String name = (e.getPath() != null && slash >= 0) ? e.getPath().substring(slash + 1) : e.getPath();
        return SkillResourceFileDTO.builder()
                .path(e.getPath())
                .type(e.getType())
                .name(name)
                .parentPath(e.getParentPath())
                .encoding(e.getEncoding())
                .mime(e.getMime())
                .content(e.getContent())
                .build();
    }

    /** Skill 检测记录 Entity → DTO（v3.0；errors JSON 列反序列化为明细列表）。 */
    private static SkillCheckRecordDTO checkRecordEntityToDTO(SkillCheckRecordEntity e) {
        return SkillCheckRecordDTO.builder()
                .num(e.getNum())
                .skillNum(e.getSkillNum())
                .version(e.getVersion())
                .result(e.getResult())
                .sizeResult(e.getSizeResult())
                .formatResult(e.getFormatResult())
                .availabilityResult(e.getAvailabilityResult())
                .errors(parseErrors(e.getErrors()))
                .costMs(e.getCostMs())
                .createNo(e.getCreateNo())
                .createTime(e.getCreateTime())
                .build();
    }

    /** errors JSON 列 → {@code List<SkillCheckErrorDTO>}；空值 / 非法 JSON 返回空集合。 */
    private static List<SkillCheckErrorDTO> parseErrors(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseObject(json, new TypeReference<List<SkillCheckErrorDTO>>() {});
        } catch (Exception ignore) {
            return new ArrayList<>();
        }
    }

    /**
     * 从版本快照资源树（owner_type=VERSION）中提取 SKILL.md 正文内容。
     *
     * @param versionNum SkillVersion 业务编号
     * @return SKILL.md 正文（去掉 front-matter）；无资源树或找不到时返回 null
     */
    private String loadSkillMdFromResourceTree(String versionNum) {
        List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                Wrappers.<SkillResourceFileEntity>lambdaQuery()
                        .eq(SkillResourceFileEntity::getOwnerType, SkillResourceFileEntity.OWNER_TYPE_VERSION)
                        .eq(SkillResourceFileEntity::getOwnerNum, versionNum)
                        .eq(SkillResourceFileEntity::getPath, SkillResourceCodec.SKILL_MD_FILENAME));
        if (rows.isEmpty()) {
            return null;
        }
        String content = rows.get(0).getContent();
        if (content == null) {
            return null;
        }
        // 去掉 front-matter（---...--- 块）
        // SKILL.md 的 front-matter 格式：第一行 ---，第二行起到下一个 --- 为 YAML 头
        // 返回 front-matter 之后的正文
        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3); // 从第 3 个字符开始找第二个 ---
            if (endIdx != -1) {
                return content.substring(endIdx + 3).trim();
            }
        }
        return content.trim();
    }
}
