package ink.garry.rd.agent.ws.domain.skill;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillDomainEventDTO;
import ink.garry.rd.agent.ws.domain.skill.gateway.SkillVersionGateway;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillVersionRepository;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SkillVersion 实体（v2.7）。
 * <p>
 * 表示一次正式发布的 Skill 版本快照。由 application 层（{@code SkillCommandService.publish}）在
 * 发布事务内通过 {@code SkillFactory.createInitialVersion} 创建并落库；一旦保存原则上不可变
 * （字段层面），但本实体自身具有<b>状态生命周期</b>（v2.7 新增）：
 * <ul>
 *   <li>{@link SkillStatus#DRAFT 草稿}：占位状态，当前版本流程一般直接进入 PUBLISHED；保留枚举值便于未来扩展</li>
 *   <li>{@link SkillStatus#PUBLISHED 已发布}：被 {@code Skill.currentVersionNum} 指向、对外可见</li>
 *   <li>{@link SkillStatus#DEPRECATED 已下架}：该具体版本被标记为不可用（如平台运维下架某个有缺陷的历史版本）</li>
 * </ul>
 * <p>
 * <b>v2.5 字段精简</b>：相对 v2.0 移除 {@code changeLevel} / {@code skillFileHash} /
 * {@code changeNote} / {@code publishedBy} / {@code publishedAt} / {@code current} 六个字段；
 * {@code version} 改为 String；新增 {@code name} 记录发布时的 Skill 名称。
 * <p>
 * <b>v2.7 新增</b>：{@link #status} 状态字段；{@code Skill} 聚合的 publish / rollbackToVersion
 * 不再访问本实体（解耦）。
 * <p>
 * <b>领域方法</b>：{@link #save(String)} / {@link #delete(String)}。事件由父聚合 {@code Skill}
 * 统一发送，本实体不自发事件。
 */
@Getter
@Setter
public class SkillVersion extends DomainEntity {

    // ---- 业务字段 ----

    /** SkillVersion 业务编号（前缀 SVN，由 {@link SkillVersionGateway#generateSkillVersionNum()} 生成）。 */
    private String num;

    /** 所属 Skill 的业务编号（外键）。 */
    private String skillNum;

    /** 版本号字符串（约定 {@code vX.Y.Z}，后端不解析语义；同 skillNum 下唯一）。 */
    private String version;

    /** Skill 发布时的名称快照。 */
    private String name;

    /** Skill 发布时的描述快照。 */
    private String description;

    /** Skill 发布时的标签快照（深拷贝）。 */
    private java.util.List<String> tags;

    /**
     * Skill 发布时的资源文件树快照（v3.0：替代旧 {@code skillFileKey} 对象存储 key）。
     * <p>不可变版本快照所含的整棵文件树（含 SKILL.md 与资源，内容随节点入库）。
     */
    private List<SkillResourceFile> resourceFiles;

    /** 归属工作空间业务编号（前缀 WS-）；由 SkillVersionFactory 在 create 时从 WorkspaceContextHolder 注入。 */
    private String workspaceNum;

    /**
     * 版本生命周期状态（v2.7 新增）。
     * <p>
     * 复用 {@link SkillStatus} 枚举（值 DRAFT / PUBLISHED / DEPRECATED 一致）；
     * v2.8：{@link #save(String)} 中若 status 为 null 默认置 {@link SkillStatus#DRAFT}，
     * 由调用方在 save 后显式 {@link #publish(String)} 切到 PUBLISHED。
     */
    private SkillStatus status;

    // ---- 装配依赖 ----

    /** 装配依赖：SkillVersion 仓储（save / findByNum / deleteByNum）。 */
    private transient SkillVersionRepository skillVersionRepository;
    /** 装配依赖：SkillVersion 网关（业务编号生成）。 */
    private transient SkillVersionGateway skillVersionGateway;
    /** 装配依赖：领域事件发布器（v2.7 起本实体不主动发事件，预留位置兼容工厂统一装配）。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（Mapper / Factory 调用）。 */
    public SkillVersion() {
    }

    // ---- 抽象方法实现 ----

    /**
     * 版本不变量：skillNum / version / name / description / status 必须存在；
     * resourceFiles 必须含根 SKILL.md；description 长度上限 5000 字符
     * （与 {@link ink.garry.rd.agent.ws.domain.skill.Skill#domainValidate} 同口径）。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(skillNum, "SkillVersion 所属 skillNum 不能为空");
        Assert.notBlank(version, "SkillVersion 版本号不能为空");
        Assert.notBlank(name, "SkillVersion 名称快照不能为空");
        Assert.notBlank(description, "SkillVersion 描述快照不能为空");
        Assert.isTrue(description.length() <= 5000, "SkillVersion 描述长度不能超过 5000 字符");
        Assert.notEmpty(resourceFiles, "SkillVersion 资源文件树快照不能为空");
        Assert.notNull(status, "SkillVersion 状态不能为空");
    }

    /**
     * 保存版本快照（一般为首次 INSERT）。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 无前置规则 → (3) 赋值（值对象初始化 + num 生成 + status 默认 DRAFT）
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布 {@code SKILL_VERSION_SAVED} 事件。
     * <p>
     * <b>v2.8</b>：默认状态由 PUBLISHED 改为 {@link SkillStatus#DRAFT}；
     * application 层 publish 流程为 {@code save → version.publish() → skill.publish()}，
     * 由 {@link #publish(String)} 显式切到 PUBLISHED。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：无前置（版本号唯一性由应用层经 Mapper 在调本方法前预检）

        // 3. 赋值：值对象初始化 + num 生成 + status 默认
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (this.resourceFiles == null) {
            this.resourceFiles = new ArrayList<>();
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = skillVersionGateway.generateSkillVersionNum();
        }
        if (this.status == null) {
            // v2.8：默认 DRAFT；调用方需显式 publish() 才会上线
            this.status = SkillStatus.DRAFT;
        }

        // 4. 完整性校验
        this.validate();

        // 5. 持久化（仅 INSERT；DB 侧 uk_skill_version_no 兜底防止 (skillNum, version) 重复）
        skillVersionRepository.save(this);

        // 6. 发布事件（v2.8：每次 save 必发，与 Skill.save 保持一致）
        publishEvent(DomainEventConstant.SKILL_VERSION_SAVED, operatorId);
    }

    /**
     * 软删除版本（标记 deleted=1）。
     * <p>
     * 仅在历史版本清理场景使用；常规业务流程不应主动删除版本（如需"下架"某个版本，
     * 改 {@link #status} 为 {@link SkillStatus#DEPRECATED} 即可）。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 num 非空 → (3) 赋值 deleted=1
     * → (4) 完整性校验 → (5) 持久化删除 → (6) 发布 {@code SKILL_VERSION_DELETED} 事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：num 必须已存在
        Assert.notBlank(this.num, "SkillVersion 业务编号不能为空");

        // 3. 赋值
        this.deleted = 1;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化删除
        skillVersionRepository.deleteByNum(this.num);

        // 6. 发布事件（v2.8：每次 delete 必发）
        publishEvent(DomainEventConstant.SKILL_VERSION_DELETED, operatorId);
    }

    /**
     * 发布：DRAFT → PUBLISHED（v2.7 新增）。
     * <p>
     * 用于"先 save 成 DRAFT，稍后单独上线"场景；常规发布路径由 {@code SkillFactory.createInitialVersion}
     * 直接置 PUBLISHED，无需走本方法。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status==DRAFT → (3) 赋值 status=PUBLISHED
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布 {@code SKILL_VERSION_ACTIVATED} 事件。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != DRAFT 时
     */
    public void publish(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：仅 DRAFT 可上线
        Assert.notNull(this.status, "SkillVersion 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.DRAFT,
                "SkillVersion {} 当前状态为 {}，仅 DRAFT 可发布", num, status);

        // 3. 赋值
        this.status = SkillStatus.PUBLISHED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillVersionRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_VERSION_ACTIVATED, operatorId);
    }

    /**
     * 下架：PUBLISHED → DEPRECATED（v2.7 新增）。
     * <p>
     * 用于平台运维场景标记某个具体历史版本不可用（例如发现该版本有缺陷），
     * 不会影响 {@code Skill.currentVersionNum} 指针本身；如需让 Skill 整体下架，
     * 走 {@code Skill.unpublish}。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status==PUBLISHED → (3) 赋值 status=DEPRECATED
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布 {@code SKILL_VERSION_DEPRECATED} 事件。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != PUBLISHED 时
     */
    public void unpublish(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：仅 PUBLISHED 可下架
        Assert.notNull(this.status, "SkillVersion 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.PUBLISHED,
                "SkillVersion {} 当前状态为 {}，仅 PUBLISHED 可下架", num, status);

        // 3. 赋值
        this.status = SkillStatus.DEPRECATED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillVersionRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_VERSION_DEPRECATED, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人用户 ID
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        // SkillVersion 事件载荷沿用 SkillDomainEventDTO 框架，只填本版本相关字段
        SkillDomainEventDTO payload = SkillDomainEventDTO.builder()
                .num(this.skillNum)
                .name(this.name)
                .version(this.version)
                .description(this.description)
                .tags(this.tags)
                .status(this.status)
                .operatorId(operatorId)
                .occurredAt(java.time.LocalDateTime.now())
                .build();
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(payload)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
