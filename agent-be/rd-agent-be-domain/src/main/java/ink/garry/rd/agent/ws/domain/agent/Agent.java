package ink.garry.rd.agent.ws.domain.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.agent.AgentDomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 聚合根。
 * <p>
 * 含 2 种创建方式 × 3 种类型；通过 currentVersionNum 指向当前在线版本（仅 CONFIG）。
 * v3.0：草稿合并到 {@link AgentVersion}，通过 {@code AgentVersionStatus.DRAFT} 标识。
 * <p>
 * <b>A2A 模式只读保护</b>：所有 mutate 业务方法（publish / offline / delete）
 * 开头必先调 {@link #assertMutableByPlatform()}，A2A 直接抛 {@code BizException(2010)}；
 * A2A 状态由 {@link #applyNacosSync} 同步任务回写。
 * <p>
 * 注意：本聚合根不直接持有 AgentVersion 实例，跨实体协作通过
 * application 层编排（满足"聚合内一致性、跨聚合通过 ID 引用"约束）。
 */
@Getter
@Setter
public class Agent extends DomainEntity implements ink.garry.rd.agent.ws.facade.domain.PublisherAware {

    /**
     * BizCode 中 {@code A2A_AGENT_UNMODIFIABLE} 的数字编码（hardcode 避免 domain → client 反向依赖）。
     * 与 {@code ink.garry.rd.agent.ws.client.common.BizCode#A2A_AGENT_UNMODIFIABLE} 保持同步。
     */
    private static final int CODE_A2A_UNMODIFIABLE = 2010;

    /** Agent 业务编号，前缀 AGT，由 AgentGateway 生成；跨聚合引用 ID */
    private String num;
    /** Agent 显示名，CONFIG 模式同 owner 下需唯一；A2A 取自 Agent Card */
    private String name;
    /** Agent 描述，用于列表展示与挂载下拉提示 */
    private String description;
    /** 业务标签（CONFIG / A2A 共用），用于分类 / 检索；可空，无业务规则约束 */
    private java.util.List<String> tags;
    /** 创建方式：CONFIG / A2A */
    private CreationMode creationMode;
    /** 行为类型：NORMAL / SUPERVISOR / ROUTER；A2A 强制 NORMAL */
    private AgentType agentType;
    /** 负责人 userId；A2A 模式固定为 system 或 Nacos 元数据 owner */
    private String ownerUserId;
    /** 归属工作空间业务编号（前缀 WS-）；由 AgentFactory 在 create 时从 WorkspaceContextHolder 注入 */
    private String workspaceNum;
    /** 生命周期状态：DRAFT_ONLY / PUBLISHED / OFFLINE；A2A 由 Nacos healthy 同步 */
    private AgentStatus status;
    /** 当前在线版本号 v1.0.0；DRAFT_ONLY 状态可为 null；A2A 永远为 null */
    private String currentVersionNum;
    /**
     * v3.0：当前在线版本 ConfigSnapshot 镜像（仅 CONFIG）。
     * <p>
     * 发布事务内由 {@link #promotePublished} 同步更新；调试 / 评测 / 挂载下拉直接读，避免 join agent_version。
     * A2A 永远为 null（A2A 不参与版本化）。
     */
    private ConfigSnapshot configSnapshot;
    /** 是否空白沙盒 Agent（不出现在列表中）；A2A 永远 false */
    private boolean sandbox;
    /** A2A 来源信息（仅 A2A 模式有值；CONFIG 永远为 null） */
    private A2aSourceInfo a2aSource;
    /** A2A 幂等键 = nacosGroup@@nacosService（仅 A2A 模式有值，冗余自 a2aSource，便于建唯一索引） */
    private String nacosServiceKey;

    // ---- 装配依赖（通过 setter 装配；Repository 由 FactoryImpl/RepositoryImpl 装配；Gateway/Publisher 由 application 层装配） ----
    /** 装配依赖：Agent 仓储，仅承担 save/findByNum/deleteByNum 三方法（CLAUDE.md §3.5） */
    private transient AgentRepository agentRepository;
    /** 装配依赖：Agent 聚合网关（生成业务编号 + 读能力） */
    private transient AgentGateway agentGateway;
    /** 装配依赖：领域事件发布器，由 application 层注入 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（Java 自动生成；显式声明便于阅读） */
    public Agent() {}

    /**
     * 领域规则校验：必填字段齐全；A2A 必须 NORMAL + currentVersionNum 必为 null；
     * 监督者 / 路由仅 CONFIG 可用；A2A 在 DRAFT_ONLY 草稿态下允许 a2aSource / nacosServiceKey
     * 为空（远端 AgentCard 尚未拉取），其余状态必须齐全。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(name, "Agent 名称不能为空");
        Assert.notNull(creationMode, "Agent 创建方式不能为空");
        Assert.notNull(agentType, "Agent 类型不能为空");
        Assert.notBlank(ownerUserId, "Agent 负责人不能为空");
        Assert.notNull(status, "Agent 状态不能为空");
        if (creationMode == CreationMode.A2A) {
            Assert.isTrue(agentType == AgentType.NORMAL,
                    "A2A Agent 不支持监督者 / 路由类型，agentType={}", agentType);
            Assert.isNull(currentVersionNum, "A2A Agent 不参与版本化，currentVersionNum 必为 null");
            if (status != AgentStatus.DRAFT_ONLY) {
                Assert.notNull(a2aSource, "A2A Agent (非草稿) 必须有 Nacos 来源信息");
                Assert.notBlank(nacosServiceKey, "A2A Agent (非草稿) 必须有 Nacos 幂等键");
            }
        } else {
            Assert.isNull(a2aSource, "CONFIG Agent 不应携带 A2A 来源信息");
            Assert.isNull(nacosServiceKey, "CONFIG Agent 不应携带 Nacos 幂等键");
        }
    }

    /**
     * 兜底守卫：A2A Agent 不允许在平台侧 mutate；前端隐藏按钮 + 后端此处兜底。
     * <p>
     * 所有 mutate 业务方法（publish / offline / delete）
     * 开头必调本方法。详见技术方案 v2.0 §6.6.4。
     *
     * @throws BusinessException A2A Agent 时抛 {@link BizCode#A2A_AGENT_UNMODIFIABLE}
     */
    public void assertMutableByPlatform() {
        if (this.creationMode == CreationMode.A2A) {
            throw new BusinessException(
                    CODE_A2A_UNMODIFIABLE,
                    "A2A Agent 不可在平台修改，请到 Nacos 操作（agentNum=" + num + "）");
        }
    }

    /**
     * 首次落库：默认状态为 DRAFT_ONLY，自动生成 num，发布 AGENT_CREATED 事件。
     * <p>
     * A2A 模式由 {@link ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory#createA2aAgent}
     * 创建后调用 save 时 status 已是 PUBLISHED / OFFLINE，本方法不修改其 status。
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化对象
        this.initialize(operatorId);

        // 2. 状态默认 DRAFT_ONLY（首次发布前，仅对未设置 status 的 CONFIG 生效）
        if (this.status == null) {
            this.status = AgentStatus.DRAFT_ONLY;
        }

        // 3. 赋值：值对象初始化、num 生成
        if (StrUtil.isBlank(this.num)) {
            this.num = agentGateway.generateAgentNum();
        }

        // 4. 同步 a2aSource → nacosServiceKey 冗余字段
        if (this.creationMode == CreationMode.A2A && this.a2aSource != null
                && StrUtil.isBlank(this.nacosServiceKey)) {
            this.nacosServiceKey = this.a2aSource.resolveServiceKey();
        }

        // 5. 领域完整性校验
        this.validate();

        // 6. 持久化
        agentRepository.save(this);

        // 7. 发布领域事件
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_CREATED, operatorId));
    }

    /**
     * 删除：发布 AGENT_DELETED 事件。
     * <p>
     * v2.6 修订：原先 A2A 被 {@link #assertMutableByPlatform()} 拦截，导致取消订阅 A2A Agent 报 2010。
     * 按 v2.6 PRD 「取消订阅 = 合法的 A2A 删除路径」放开 assertMutableByPlatform 调用，
     * 且不再强制要求 OFFLINE 状态——A2A Agent 由 Nacos 健康度推动状态，
     * 平台侧"取消订阅"可在任意状态触发。CONFIG 仍走旧规则：仅 OFFLINE 可删。
     */
    @Override
    public void delete(String operatorId) {
        // v2.6：不再调 this.assertMutableByPlatform()；A2A 取消订阅走此路径，需要放行。
        // 1. 初始化（更新 updateNo/updateTime）
        this.initialize(operatorId);
        // 2. 领域规则：CONFIG 模式仍要求 OFFLINE；A2A 模式跳过该约束（由 Nacos 同步驱动状态）
        Assert.notNull(this.status, "Agent 状态不能为空");
        if (this.creationMode != CreationMode.A2A) {
            Assert.isTrue(this.status == AgentStatus.OFFLINE,
                    "Agent {} 当前状态为 {}，仅允许 OFFLINE 状态删除", num, status);
        }
        // 3. 赋值
        this.deleted = 1;
        // 4. 完整性校验
        this.validate();
        // 5. 持久化删除
        agentRepository.deleteByNum(this.num);
        // 6. 发布事件
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_DELETED, operatorId));
    }

    /**
     * 业务方法：发布新版本后切换在线版本号 + 状态 + ConfigSnapshot 镜像。仅 CONFIG 模式可用。
     * <p>
     * v3.0：发布事务内同步更新 {@code agent.config_snapshot}；调试 / 评测 / 挂载下拉直接读 agent，
     * 不再 join agent_version。
     * 在 application 的发布事务中调用。
     *
     * @param newVersionNum 新版本号字符串
     * @param newSnapshot   新版本 ConfigSnapshot；用于同步落到 agent.config_snapshot 镜像
     * @param operatorId    操作人 userId
     */
    public void promotePublished(String newVersionNum, ConfigSnapshot newSnapshot, String operatorId) {
        // 0. A2A 拦截
        this.assertMutableByPlatform();
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 校验
        Assert.notBlank(newVersionNum, "新版本号不能为空");
        Assert.notNull(newSnapshot, "新版本 ConfigSnapshot 不能为空");
        // 3. 赋值
        this.currentVersionNum = newVersionNum;
        this.configSnapshot = newSnapshot;
        this.status = AgentStatus.PUBLISHED;
        // 4. 完整性校验
        this.validate();
        // 5. 持久化
        agentRepository.save(this);
        // 6. 发布事件
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_VERSION_PUBLISHED, operatorId,
                newVersionNum));
    }

    /**
     * 业务方法：手动下线（仅 CONFIG 模式；A2A 由 Nacos 同步触发）。
     */
    public void offline(String operatorId) {
        // 0. A2A 拦截
        this.assertMutableByPlatform();
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 领域规则
        Assert.isTrue(this.status == AgentStatus.PUBLISHED,
                "Agent {} 当前状态 {}，仅 PUBLISHED 可下线", num, status);
        // 3. 赋值
        this.status = AgentStatus.OFFLINE;
        // 4. 完整性校验
        this.validate();
        // 5. 持久化
        agentRepository.save(this);
        // 6. 发布事件
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_OFFLINED, operatorId));
    }

    /**
     * A2A 模式：从 Nacos 全字段覆盖更新。
     * <p>
     * 用于订阅推送 / 兜底轮询 / 手动重新同步三个入口；本地任何字段修改下次同步都会被刷掉。
     * 状态由 UP→DOWN 时附带触发 {@code AGENT_OFFLINED} 事件用于通知调试台 / 评测下拉刷新。
     *
     * @param newName        最新名称（取自 Agent Card name）
     * @param newDescription 最新描述（取自 Agent Card description，可空）
     * @param newSource      最新的 A2A 来源信息（覆盖现有 a2aSource 全字段）
     * @param newStatus      最新状态（按 Nacos instance.healthy 映射 PUBLISHED / OFFLINE）
     * @param eventType      本次同步事件来源
     * @param operatorId     操作人，订阅 / 轮询固定为 nacos-sync，手动同步为操作用户 userId
     */
    public void applyNacosSync(String newName, String newDescription, A2aSourceInfo newSource,
                               AgentStatus newStatus, SyncEventType eventType, String operatorId) {
        Assert.notBlank(newName, "A2A 同步 name 不能为空");
        Assert.notNull(newSource, "A2A 同步 source 不能为空");
        Assert.notNull(newStatus, "A2A 同步 status 不能为空");
        Assert.notNull(eventType, "A2A 同步 eventType 不能为空");
        Assert.isTrue(this.creationMode == CreationMode.A2A,
                "applyNacosSync 仅适用 A2A 模式，当前 creationMode={}", creationMode);

        // 1. 初始化（更新 updateNo/updateTime）
        this.initialize(operatorId);

        // 2. 全字段覆盖（来自 Nacos 的字段都覆盖；本地任何篡改丢弃）
        AgentStatus previousStatus = this.status;
        this.name = newName;
        this.description = newDescription;
        this.a2aSource = newSource;
        this.nacosServiceKey = newSource.resolveServiceKey();
        this.status = newStatus;

        // 3. 完整性校验
        this.validate();

        // 4. 持久化
        agentRepository.save(this);

        // 5. 发事件：先 AGENT_A2A_SYNCED，再（按需）AGENT_OFFLINED
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_A2A_SYNCED, operatorId, null));
        if (previousStatus != AgentStatus.OFFLINE && newStatus == AgentStatus.OFFLINE) {
            domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_OFFLINED, operatorId));
        }
    }

    private DomainEventDTO buildEvent(String type, String operatorId) {
        return buildEvent(type, operatorId, null);
    }

    /** 构建 Agent 领域事件载荷（含 versionNum 透传，便于下游订阅） */
    private DomainEventDTO buildEvent(String type, String operatorId, String versionNum) {
        AgentDomainEventDTO payload = AgentDomainEventDTO.builder()
                .agentNum(this.num)
                .versionNum(versionNum != null ? versionNum : this.currentVersionNum)
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
        return DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(payload)
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
    }
}
