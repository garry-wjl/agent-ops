package ink.garry.rd.agent.ws.domain.model;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.model.dto.ModelDomainEventDTO;
import ink.garry.rd.agent.ws.domain.model.gateway.ModelGateway;
import ink.garry.rd.agent.ws.domain.model.repository.ModelRepository;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Model 聚合根。
 * <p>
 * LLM 模型接入资源，工作空间级归属，承载三态生命周期（详见模型管理技术方案 §4.2）。
 * 状态机：
 * <pre>
 *   新建 ──save──▶ 草稿 DRAFT ──enable──▶ 启用 ENABLED
 *                    │  ▲                    │  ▲
 *                  delete │              disable │
 *                    ▼  └────── enable ─────────┘
 *                  (软删)        禁用 DISABLED
 * </pre>
 * <p>
 * <b>职责边界</b>：本聚合是<b>纯状态机 + 字段不变量校验</b> —— 只负责状态流转、校验、持久化与发事件，
 * <b>不</b>执行任何外部 HTTP（无连通性测试，PRD §2.2）。API Key 在领域内始终持有<b>明文</b>
 * （{@link #apiKey}）；密文 ↔ 明文转换是 infra 持久化细节，由 {@code ModelRepositoryImpl} 在
 * Entity ↔ 领域对象映射时调用 {@code SecretCipher} 完成，不在本聚合体现。
 * <ul>
 *   <li>{@link #save(String)}：新增 / 编辑 upsert（status 兜底 DRAFT，编辑保持原 status），发 {@code MODEL_SAVED}。</li>
 *   <li>{@link #enable(String)}：DRAFT / DISABLED → ENABLED，发 {@code MODEL_ENABLED}。</li>
 *   <li>{@link #disable(String)}：ENABLED → DISABLED，发 {@code MODEL_DISABLED}。</li>
 *   <li>{@link #delete(String)}：仅 DRAFT 可软删，发 {@code MODEL_DELETED}。</li>
 * </ul>
 */
@Getter
@Setter
public class Model extends DomainEntity {

    /** 名称长度上限（与 client.model.constant.ModelConstants 保持一致）。 */
    private static final int NAME_MAX_LENGTH = 128;
    /** 模型标识长度上限。 */
    private static final int MODEL_ID_MAX_LENGTH = 128;
    /** 备注长度上限。 */
    private static final int REMARK_MAX_LENGTH = 500;
    /** Base URL 长度上限。 */
    private static final int BASE_URL_MAX_LENGTH = 512;

    // ---- 业务字段 ----

    /** 模型业务编号（前缀 MDL，由 {@link ModelGateway#generateModelNum()} 生成）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 归属范围：SPACE / PLATFORM。 */
    private ModelScope scope;

    /** 模型名称；同一工作空间内唯一（应用层经唯一性预检 + DB 唯一索引兜底）。 */
    private String name;

    /** 用户填写的模型标识；同一工作空间内唯一。 */
    private String modelId;

    /** 模型 API Key <b>明文</b>（领域内只持有明文；密文转换在 infra Repository ↔ Gateway 完成）。 */
    private String apiKey;

    /** 模型服务端点 Base URL（须以 http:// 或 https:// 开头）。 */
    private String baseUrl;

    /** 生命周期状态；详见 {@link ModelStatus}。 */
    private ModelStatus status;

    /** 备注（可空，≤500 字）。 */
    private String remark;

    // ---- 装配依赖（由 ModelFactory 在创建时装配） ----

    /** 装配依赖：Model 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient ModelRepository modelRepository;
    /** 装配依赖：Model 业务编号生成网关。 */
    private transient ModelGateway modelGateway;
    /** 装配依赖：领域事件发布器。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（infra RepositoryImpl 按 num 重建聚合时用无参 + setter 装配）。 */
    public Model() {
    }

    /**
     * 必填字段 + 装配依赖构造方法（由 {@code ModelFactory.buildModel} 创建新聚合时调用）。
     * <p>
     * 仅接收构建聚合所必须的用户可填字段与三个装配依赖；不接收由状态机控制的 status、
     * 系统生成的 num 以及审计字段，这些在 {@link #save(String)} 中统一处理。
     *
     * @param workspaceNum         归属工作空间业务编号
     * @param name                 模型名称
     * @param modelId              用户填写的模型标识
     * @param apiKey               API Key 明文
     * @param baseUrl              模型服务端点 Base URL
     * @param remark               备注（可空）
     * @param modelRepository      Model 仓储
     * @param modelGateway         Model 业务编号生成网关
     * @param domainEventPublisher 领域事件发布器
     */
    public Model(String workspaceNum,
                 ModelScope scope,
                 String name,
                 String modelId,
                 String apiKey,
                 String baseUrl,
                 String remark,
                 ModelRepository modelRepository,
                 ModelGateway modelGateway,
                 DomainEventPublisher domainEventPublisher) {
        this.workspaceNum = workspaceNum;
        this.scope = scope;
        this.name = name;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.remark = remark;
        this.modelRepository = modelRepository;
        this.modelGateway = modelGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：name / modelId / apiKey / baseUrl / status 非空且合法；
     * name ≤128、modelId ≤128、remark ≤500、baseUrl ≤512 且以 http(s):// 开头。
     */
    @Override
    public void domainValidate() {
        // 归属范围与工作空间一致性
        if (scope == null) {
            scope = ModelScope.SPACE;
        }
        if (scope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型归属工作空间编号不能为空");
        }
        if (scope == ModelScope.PLATFORM) {
            Assert.isTrue(StrUtil.isBlank(workspaceNum), "系统模型归属工作空间编号必须为空");
        }
        // 名称 [1, 128]
        Assert.notBlank(name, "模型名称不能为空");
        Assert.isTrue(name.length() <= NAME_MAX_LENGTH, "模型名称长度不能超过 128 字");
        // 模型标识 [1, 128]
        Assert.notBlank(modelId, "模型标识不能为空");
        Assert.isTrue(modelId.length() <= MODEL_ID_MAX_LENGTH, "模型标识长度不能超过 128 字");
        // API Key 明文非空
        Assert.notBlank(apiKey, "模型 API Key 不能为空");
        // Base URL 非空、长度、格式
        Assert.notBlank(baseUrl, "模型 Base URL 不能为空");
        Assert.isTrue(baseUrl.length() <= BASE_URL_MAX_LENGTH, "模型 Base URL 长度不能超过 512 字");
        Assert.isTrue(StrUtil.startWithAny(baseUrl, "http://", "https://"),
                "模型 Base URL 须以 http:// 或 https:// 开头");
        // 备注 ≤500
        Assert.isTrue(remark == null || remark.length() <= REMARK_MAX_LENGTH, "备注不超过 500 字");
        // 状态
        Assert.notNull(status, "模型状态不能为空");
    }

    /**
     * 保存 / 编辑模型（创建与编辑统一入口）。
     * <p>
     * 六步顺序：(1) 初始化审计字段 → (2) save 本身无前置状态约束 → (3) 赋值（status 兜底 DRAFT +
     * num 为空时经网关生成）→ (4) 领域完整性校验 → (5) 持久化 → (6) 发布 {@code MODEL_SAVED}。
     * 编辑时由应用层 set 可变更字段（name / modelId / baseUrl / remark / apiKey）后调用本方法，
     * 整聚合覆盖落库；status 不在此变更（启停由独立动作处理）。
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. save 本身无前置状态约束

        // 3. 赋值：status 兜底 DRAFT；num 为空时经网关生成
        if (this.status == null) {
            this.status = ModelStatus.DRAFT;
        }
        if (this.scope == null) {
            this.scope = ModelScope.SPACE;
        }
        if (this.scope == ModelScope.PLATFORM) {
            this.workspaceNum = null;
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = modelGateway.generateModelNum();
        }

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（upsert 语义）
        modelRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.MODEL_SAVED, operatorId);
    }

    /**
     * 逻辑删除模型（仅草稿态可删）。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status == DRAFT → (3) 置 deleted=1 → (4) 完整性校验
     * → (5) 软删 → (6) 发布 {@code MODEL_DELETED}。
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：仅草稿态可删除
        Assert.isTrue(this.status == ModelStatus.DRAFT, "仅草稿态模型可删除");

        // 3. 赋值：置逻辑删除标识
        this.deleted = 1;

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化删除（infra deleteByNum 实现为软删 UPDATE deleted=1）
        modelRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.MODEL_DELETED, operatorId);
    }

    // ---- 状态流转领域动作 ----

    /**
     * 启用：DRAFT / DISABLED → ENABLED（仅切状态，不做连通性测试）。
     *
     * @param operatorId 操作人工号
     */
    public void enable(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅草稿 / 禁用态可启用
        Assert.isTrue(this.status == ModelStatus.DRAFT || this.status == ModelStatus.DISABLED,
                "仅草稿态或禁用态模型可启用");
        // 3. 赋值：状态流转
        this.status = ModelStatus.ENABLED;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        modelRepository.save(this);
        // 6. 发布事件
        publishEvent(DomainEventConstant.MODEL_ENABLED, operatorId);
    }

    /**
     * 禁用：ENABLED → DISABLED。
     *
     * @param operatorId 操作人工号
     */
    public void disable(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅启用态可禁用
        Assert.isTrue(this.status == ModelStatus.ENABLED, "仅启用态模型可禁用");
        // 3. 赋值：状态流转
        this.status = ModelStatus.DISABLED;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        modelRepository.save(this);
        // 6. 发布事件
        publishEvent(DomainEventConstant.MODEL_DISABLED, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     * <p>事件载荷 {@link ModelDomainEventDTO} 不含任何密钥字段（防泄露）。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人工号
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(ModelDomainEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
