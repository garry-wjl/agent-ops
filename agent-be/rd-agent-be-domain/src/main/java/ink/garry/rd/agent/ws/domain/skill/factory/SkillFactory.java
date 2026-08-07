package ink.garry.rd.agent.ws.domain.skill.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.SkillVersion;
import ink.garry.rd.agent.ws.domain.skill.gateway.SkillGateway;
import ink.garry.rd.agent.ws.domain.skill.gateway.SkillVersionGateway;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillRepository;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillVersionRepository;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 领域工厂（v2.9：固定 4 方法）。
 * <p>
 * 仅有 4 个 build 方法，分别覆盖 Skill / SkillVersion 的两种构建场景：
 * <ul>
 *   <li>{@link #buildSkill}：用必要字段构造一条新的 Skill（未落库）；status 自动置 {@link SkillStatus#DRAFT}。</li>
 *   <li>{@link #buildSkillByNum}：按业务编号从仓储加载 Skill 并装配依赖。</li>
 *   <li>{@link #buildSkillVersion}：用必要字段构造一条新的 SkillVersion（未落库）；status 由
 *       {@code SkillVersion.save} 兜底为 {@link SkillStatus#DRAFT}（v2.8）。</li>
 *   <li>{@link #buildSkillVersionByNum}：按业务编号从仓储加载 SkillVersion 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>：本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。
 * 创建出的领域对象（{@code Skill} / {@code SkillVersion}）由工厂手动 wire 所需的 Repository /
 * Gateway / EventPublisher，使调用方可直接执行业务方法（save / publish / unpublish / 等）。
 */
@Component
public class SkillFactory {

    @Resource
    private SkillRepository skillRepository;
    @Resource
    private SkillGateway skillGateway;
    @Resource
    private SkillVersionRepository skillVersionRepository;
    @Resource
    private SkillVersionGateway skillVersionGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要字段构造一条新的 Skill 聚合（未落库）；status 自动置 {@link SkillStatus#DRAFT}。
     * <p>
     * 调用方拿到返回的 Skill 后通常立即调用 {@link Skill#save(String)} 完成首次落库；
     * 后续可经 {@link Skill#submitForCheck(String, String)} + 检测 + {@link Skill#publish(String, String)}
     * 触发首版发布。
     *
     * @param name          展示名称
     * @param description   描述信息
     * @param tags          标签列表（可空；工厂内会替换为空集合）
     * @param resourceFiles 资源文件树（v3.0：含根 SKILL.md 与资源，内容随节点入库；应在调用前由应用层解析/组装完成）
     * @param source        来源（{@link SkillSource#SELF 自建} 或 {@link SkillSource#COMPANY 公司库}）
     * @param ownerUserId   负责人用户 ID
     * @return 已装配完依赖、可直接 save 的 Skill 聚合
     */
    public Skill buildSkill(String name,
                            String description,
                            List<String> tags,
                            List<SkillResourceFile> resourceFiles,
                            SkillSource source,
                            String ownerUserId) {
        Assert.notBlank(name, "Skill 名称不能为空");
        Assert.notBlank(description, "Skill 描述不能为空");
        Assert.notEmpty(resourceFiles, "Skill 资源文件树不能为空");
        Assert.notNull(source, "Skill 来源不能为空");
        Assert.notBlank(ownerUserId, "Skill 负责人不能为空");

        Skill skill = new Skill();
        skill.setName(name);
        skill.setDescription(description);
        skill.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
        skill.setResourceFiles(new ArrayList<>(resourceFiles));
        skill.setSource(source);
        skill.setOwnerUserId(ownerUserId);

        wireSkill(skill);
        return skill;
    }

    /**
     * 按业务编号加载 Skill 并装配依赖（等价于 {@code skillRepository.findByNum(num)} + wire）。
     *
     * @param num Skill 业务编号
     * @return 装配完依赖的 Skill 聚合；不存在时返回 {@code null}
     */
    public Skill buildSkillByNum(String num) {
        Assert.notBlank(num, "Skill 业务编号不能为空");
        Skill skill = skillRepository.findByNum(num);
        if (skill == null) {
            return null;
        }
        wireSkill(skill);
        return skill;
    }

    /**
     * 用必要字段构造一条新的 SkillVersion 实体（未落库）。
     * <p>
     * status 不在本方法显式赋值，由 {@link SkillVersion#save(String)} 在 status 为 null 时
     * 兜底为 {@link SkillStatus#DRAFT}（v2.8）；调用方需在 save 后显式
     * {@link SkillVersion#publish(String)} 才会切到 PUBLISHED。
     *
     * @param skillNum      所属 Skill 业务编号
     * @param version       版本号字符串（约定 {@code vX.Y.Z}）
     * @param name          发布时的 Skill 名称快照
     * @param description   发布时的描述快照
     * @param tags          发布时的标签快照（可空；工厂内会替换为空集合）
     * @param resourceFiles 发布时的资源文件树快照（v3.0：替代旧 skillFileKey）
     * @return 已装配完依赖、可直接 save 的 SkillVersion 实体
     */
    public SkillVersion buildSkillVersion(String skillNum,
                                          String version,
                                          String name,
                                          String description,
                                          List<String> tags,
                                          List<SkillResourceFile> resourceFiles) {
        Assert.notBlank(skillNum, "skillNum 不能为空");
        Assert.notBlank(version, "version 不能为空");
        Assert.notBlank(name, "name 不能为空");
        Assert.notBlank(description, "description 不能为空");
        Assert.notEmpty(resourceFiles, "resourceFiles 不能为空");

        SkillVersion skillVersion = new SkillVersion();
        skillVersion.setSkillNum(skillNum);
        skillVersion.setVersion(version);
        skillVersion.setName(name);
        skillVersion.setDescription(description);
        skillVersion.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
        skillVersion.setResourceFiles(new ArrayList<>(resourceFiles));
        // status 不在此处赋值，由 SkillVersion.save 兜底为 DRAFT

        wireSkillVersion(skillVersion);
        return skillVersion;
    }

    /**
     * 按业务编号加载 SkillVersion 并装配依赖（等价于 {@code skillVersionRepository.findByNum(num)} + wire）。
     *
     * @param num SkillVersion 业务编号
     * @return 装配完依赖的 SkillVersion 实体；不存在时返回 {@code null}
     */
    public SkillVersion buildSkillVersionByNum(String num) {
        Assert.notBlank(num, "SkillVersion 业务编号不能为空");
        SkillVersion skillVersion = skillVersionRepository.findByNum(num);
        if (skillVersion == null) {
            return null;
        }
        wireSkillVersion(skillVersion);
        return skillVersion;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Skill 聚合根（v2.7：聚合不再持有 SkillVersion 相关依赖）。 */
    private void wireSkill(Skill skill) {
        skill.setSkillRepository(this.skillRepository);
        skill.setSkillGateway(this.skillGateway);
        skill.setDomainEventPublisher(this.domainEventPublisher);
    }

    /** 把 3 个依赖一次性注入 SkillVersion 实体。 */
    private void wireSkillVersion(SkillVersion version) {
        version.setSkillVersionRepository(this.skillVersionRepository);
        version.setSkillVersionGateway(this.skillVersionGateway);
        version.setDomainEventPublisher(this.domainEventPublisher);
    }
}
