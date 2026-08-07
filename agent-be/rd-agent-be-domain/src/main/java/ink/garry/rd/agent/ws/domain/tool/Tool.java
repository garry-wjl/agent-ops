package ink.garry.rd.agent.ws.domain.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.tool.dto.ToolDomainEventDTO;
import ink.garry.rd.agent.ws.domain.tool.gateway.ToolGateway;
import ink.garry.rd.agent.ws.domain.tool.repository.ToolRepository;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiEndpoint;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiParam;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointMeta;
import ink.garry.rd.agent.ws.domain.tool.valueobject.McpConfigType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ProxyHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tool 聚合根（工具管理 v1.0）。
 * <p>
 * 表示一个可被 Agent 挂载的工具资产，覆盖 MCP（远程连接 / API 打包）与 FunctionCall
 * （OpenAPI Spec 导入 / 手动录入）两类、四种创建方式（详见工具管理技术方案 §4.2）。
 * <p>
 * <b>三态机</b>（复用 Skill 模式）：
 * <pre>
 *   新建 ──save──▶ 草稿 DRAFT ──publish──▶ 已发布 PUBLISHED ──unpublish──▶ 已废弃 DEPRECATED
 *                    ▲                          │                              │
 *                    └────── 编辑(updateTool)───┘            ◀──republish──────┘
 *   草稿 DRAFT ──delete──▶ 物理删除
 * </pre>
 * <p>
 * <b>职责边界</b>：本聚合是纯状态机 + 形态校验 —— 负责状态流转、形态字段不变量校验、
 * 发布时端点元数据解析、持久化与发事件；<b>不</b>承担运行时调用（MCP 代理转发、
 * OpenApiToMcpAdapter、变量注入等留作后续 agentrunner 方案）。
 * <p>
 * <b>领域动作</b>：
 * <ul>
 *   <li>{@link #save(String)}：保存 / 编辑工具元信息（草稿态 upsert）</li>
 *   <li>{@link #publish(String)}：草稿 → 已发布（全字段校验 + OpenAPI 端点解析）</li>
 *   <li>{@link #unpublish(String)}：已发布 → 已废弃（弃用）</li>
 *   <li>{@link #republish(String)}：已废弃 → 已发布（重新发布）</li>
 *   <li>{@link #delete(String)}：删除草稿（仅 DRAFT 可进入；infra 物理删）</li>
 * </ul>
 * <p>
 * 业务码段：7xxx 工具域（7001 参数非法 / 7002 MCP 配置非法 / 7003 打包配置非法 /
 * 7004 OpenAPI 非法 / 7005 端点配置非法 / 7006 代理配置非法 / 7007 状态非法）。
 */
@Getter
@Setter
public class Tool extends DomainEntity {

    // ---- 约束常量 ----

    /** 名称长度上限。 */
    private static final int NAME_MAX_LENGTH = 128;
    /** 描述长度上限。 */
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    /** 标签数量上限。 */
    private static final int TAGS_MAX_COUNT = 20;
    /** 单标签长度上限。 */
    private static final int TAG_MAX_LENGTH = 32;
    /** MCP 配置 JSON 大小上限（64KB，按字符数近似）。 */
    private static final int MCP_CONFIG_MAX_LENGTH = 64 * 1024;
    /** OpenAPI 文档大小上限（1MB，按字符数近似）。 */
    private static final int OPENAPI_SPEC_MAX_LENGTH = 1024 * 1024;
    /** 透传请求头数量上限。 */
    private static final int PROXY_HEADERS_MAX_COUNT = 20;
    /** 单工具端点数量上限。 */
    private static final int ENDPOINTS_MAX_COUNT = 50;

    /** 变量占位符合法形式：{字母数字下划线}。 */
    private static final Pattern VARIABLE_PLACEHOLDER = Pattern.compile("\\{\\w+}");
    /** path 占位符提取：{paramName}。 */
    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    // ---- 通用业务字段 ----

    /** 工具业务编号（MCP... / FC...，由 {@link ToolGateway#generateToolNum(ToolType)} 生成）。 */
    private String num;

    /** 归属工作空间业务编号；由 ToolFactory 在 create 时从 WorkspaceContextHolder 注入。 */
    private String workspaceNum;

    /** 工具名称；同工作空间内唯一（不区分类型，应用层经 Mapper 预检 + DB 唯一索引兜底）。 */
    private String name;

    /** 工具描述；列表展示 + Agent 挂载时给 LLM 看。 */
    private String description;

    /** 工具类型（MCP / FUNCTION_CALL）；建好不可改。 */
    private ToolType type;

    /** 创建方式；建好不可改。 */
    private CreationMode creationMode;

    /** 自由标签数组；列表 facet 筛选用。 */
    private List<String> tags;

    /** 工具生命周期状态（DRAFT / PUBLISHED / DEPRECATED）。 */
    private ToolStatus status;

    /** 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    // ---- MCP 远程连接专有字段（type=MCP, creationMode=REMOTE） ----

    /** MCP 配置子类型（LOCAL / REMOTE），决定 mcpConfig 结构。 */
    private McpConfigType mcpConfigType;

    /** MCP 配置 JSON 串原文（≤64KB）。 */
    private String mcpConfig;

    // ---- MCP 代理字段（REMOTE / API_PACKAGE 共用） ----

    /** 是否启用平台 MCP 代理；默认 false。 */
    private Boolean proxyEnabled;

    /** 透传请求头列表（proxyEnabled=true 时可填，上限 20 条）。 */
    private List<ProxyHeader> proxyHeaders;

    // ---- MCP API 打包专有字段（type=MCP, creationMode=API_PACKAGE） ----

    /** 打包方式（EXISTING_API / OPENAPI_PASTE）。 */
    private PackageMode packageMode;

    /** 来源 FunctionCall 工具的 num（packageMode=EXISTING_API 时必填，动态跟随）。 */
    private String sourceFcToolNum;

    // ---- OpenAPI 形态字段（FC-OPENAPI_SPEC / MCP-API_PACKAGE-OPENAPI_PASTE 共用） ----

    /** 粘贴的 OpenAPI / Swagger JSON 原文（≤1MB）。 */
    private String openApiSpec;

    // ---- FunctionCall 手动录入专有字段（type=FUNCTION_CALL, creationMode=MANUAL） ----

    /** API Base URL，所有端点共用。 */
    private String baseUrl;

    /** API 端点列表（≥1 且 ≤50）。 */
    private List<ApiEndpoint> endpoints;

    // ---- 系统派生字段 ----

    /** 发布时解析的端点元数据（OpenAPI 形态）；草稿态可能为空。 */
    private EndpointMeta endpointMeta;

    // ---- 装配依赖（由 ToolFactory 装配） ----

    /** 装配依赖：Tool 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient ToolRepository toolRepository;
    /** 装配依赖：Tool 业务编号生成 + OpenAPI 解析网关。 */
    private transient ToolGateway toolGateway;
    /** 装配依赖：领域事件发布器。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（infra RepositoryImpl 按 num 重建聚合时用无参 + setter 装配）。 */
    public Tool() {
    }

    /**
     * 必填字段 + 装配依赖构造方法（由 {@code ToolFactory.buildTool} 创建新聚合时调用）。
     * <p>
     * 接收创建期用户可填的业务字段（含各形态专有字段）与三个装配依赖；不接收由状态机控制的
     * status、系统生成的 num、系统派生的 endpointMeta 以及审计字段，这些在 {@link #save(String)}
     * 与各领域动作中统一处理。
     *
     * @param workspaceNum         归属工作空间业务编号
     * @param name                 工具名称
     * @param description          工具描述
     * @param type                 工具类型
     * @param creationMode         创建方式
     * @param tags                 标签（可空）
     * @param ownerUserId          负责人 / 创建人用户 ID
     * @param mcpConfigType        MCP 配置子类型（仅 MCP-REMOTE）
     * @param mcpConfig            MCP 配置 JSON 原文（仅 MCP-REMOTE）
     * @param proxyEnabled         是否启用代理（MCP 两形态）
     * @param proxyHeaders         透传请求头（MCP 两形态）
     * @param packageMode          打包方式（仅 MCP-API_PACKAGE）
     * @param sourceFcToolNum      来源 FC 工具 num（仅 EXISTING_API）
     * @param openApiSpec          OpenAPI 原文（OPENAPI_SPEC / OPENAPI_PASTE）
     * @param baseUrl              Base URL（仅 FC-MANUAL）
     * @param endpoints            端点列表（仅 FC-MANUAL）
     * @param toolRepository       Tool 仓储
     * @param toolGateway          Tool 网关
     * @param domainEventPublisher 领域事件发布器
     */
    public Tool(String workspaceNum,
                String name,
                String description,
                ToolType type,
                CreationMode creationMode,
                List<String> tags,
                String ownerUserId,
                McpConfigType mcpConfigType,
                String mcpConfig,
                Boolean proxyEnabled,
                List<ProxyHeader> proxyHeaders,
                PackageMode packageMode,
                String sourceFcToolNum,
                String openApiSpec,
                String baseUrl,
                List<ApiEndpoint> endpoints,
                ToolRepository toolRepository,
                ToolGateway toolGateway,
                DomainEventPublisher domainEventPublisher) {
        this.workspaceNum = workspaceNum;
        this.name = name;
        this.description = description;
        this.type = type;
        this.creationMode = creationMode;
        this.tags = tags;
        this.ownerUserId = ownerUserId;
        this.mcpConfigType = mcpConfigType;
        this.mcpConfig = mcpConfig;
        this.proxyEnabled = proxyEnabled;
        this.proxyHeaders = proxyHeaders;
        this.packageMode = packageMode;
        this.sourceFcToolNum = sourceFcToolNum;
        this.openApiSpec = openApiSpec;
        this.baseUrl = baseUrl;
        this.endpoints = endpoints;
        this.toolRepository = toolRepository;
        this.toolGateway = toolGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验（基础字段 + 通用约束）。
     * <p>
     * 仅校验所有状态下都必须满足的基础不变量（name / description / type / creationMode /
     * workspaceNum / ownerUserId / status / tags / 代理配置）；形态完整性（mcpConfig / openApiSpec /
     * endpoints 等）只在发布路径由 {@link #validateByShape()} 校验，草稿态允许形态字段缺失。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(name, "工具名称不能为空");
        Assert.isTrue(name.length() <= NAME_MAX_LENGTH, "工具名称长度不能超过 128 字符");
        // description 仅在发布路径校验（domainValidate 被 save/publish 共用，草稿态允许 description 为空）
        if (description != null) {
            Assert.isTrue(description.length() <= DESCRIPTION_MAX_LENGTH, "工具描述长度不能超过 500 字符");
        }
        Assert.notBlank(workspaceNum, "归属工作空间编号不能为空");
        Assert.notBlank(ownerUserId, "工具负责人不能为空");
        Assert.notNull(type, "工具类型不能为空");
        Assert.notNull(creationMode, "工具创建方式不能为空");
        Assert.notNull(status, "工具状态不能为空");
        // type 与 creationMode 组合合法性
        assertTypeModeMatch();
        // 标签约束
        if (tags != null) {
            Assert.isTrue(tags.size() <= TAGS_MAX_COUNT, "工具标签数量不能超过 20 个");
            for (String tag : tags) {
                Assert.notBlank(tag, "工具标签不能含空白项");
                Assert.isTrue(tag.length() <= TAG_MAX_LENGTH, "工具单个标签长度不能超过 32 字符");
            }
        }
        // 代理配置基础约束（任何状态都校验）
        validateProxy();
    }

    /**
     * 保存 / 编辑工具元信息（草稿态 upsert）。
     * <p>
     * 创建仅草稿（status 兜底 DRAFT）；编辑已发布工具时由应用层先把 status set 回 DRAFT 再调本方法。
     * 六步顺序：(1) 初始化审计字段 → (2) 赋值（status 兜底 DRAFT + 值对象集合初始化 + num 生成）
     * → (3) 完整性校验（基础不变量，不校验形态完整性）→ (4) 持久化 → (5) 发布 TOOL_SAVED。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：save 本身无前置状态约束（状态流转交由 publish / unpublish / republish）

        // 3. 赋值：status 兜底 DRAFT；值对象集合初始化；proxyEnabled 兜底 false；num 为空则经网关生成
        if (this.status == null) {
            this.status = ToolStatus.DRAFT;
        }
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (this.proxyEnabled == null) {
            this.proxyEnabled = Boolean.FALSE;
        }
        if (this.proxyHeaders == null) {
            this.proxyHeaders = new ArrayList<>();
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = toolGateway.generateToolNum(this.type);
        }

        // 4. 领域完整性校验（基础不变量）
        this.validate();

        // 5. 持久化（upsert 语义，不区分新增 / 更新）
        toolRepository.save(this);

        // 6. 发布事件（每次 save 必发，禁止 wasNew 式判断）
        publishEvent(DomainEventConstant.TOOL_SAVED, operatorId);
    }

    /**
     * 发布：草稿 → 已发布。
     * <p>
     * 走全字段必填 + 形态完整性校验；OpenAPI 形态（OPENAPI_SPEC / API_PACKAGE-OPENAPI_PASTE）
     * 经网关解析端点元数据落 {@link #endpointMeta}。
     * 六步顺序：(1) 初始化 → (2) 校验 status==DRAFT → (3) 赋值（OpenAPI 解析 + status=PUBLISHED）
     * → (4) 完整性校验（基础 + 形态）→ (5) 持久化 → (6) 发布 TOOL_PUBLISHED。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != DRAFT 或形态校验不通过时
     */
    public void publish(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：仅草稿态可发布
        Assert.notNull(this.status, "工具状态不能为空");
        if (this.status != ToolStatus.DRAFT) {
            throw new BusinessException(7007, "工具 " + num + " 当前状态为 " + status + "，仅草稿态可发布");
        }

        // 3. 赋值：OpenAPI 形态解析端点元数据 + 状态流转
        if (isOpenApiShape()) {
            this.endpointMeta = toolGateway.parseOpenApi(this.openApiSpec);
        }
        this.status = ToolStatus.PUBLISHED;

        // 4. 领域完整性校验：基础不变量 + 形态完整性
        this.validate();
        this.validateByShape();

        // 5. 持久化
        toolRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.TOOL_PUBLISHED, operatorId);
    }

    /**
     * 弃用：已发布 → 已废弃。
     * <p>
     * 已挂载它的 Agent 仍可调用，仅从挂载下拉移除；被 MCP API 打包引用的 FC 工具不可弃用的引用检查
     * 由应用层经 QueryService 前置完成。
     * 六步顺序：(1) 初始化 → (2) 校验 status==PUBLISHED → (3) status=DEPRECATED
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布 TOOL_DEPRECATED。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != PUBLISHED 时
     */
    public void unpublish(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：仅已发布可弃用
        Assert.notNull(this.status, "工具状态不能为空");
        if (this.status != ToolStatus.PUBLISHED) {
            throw new BusinessException(7007, "工具 " + num + " 当前状态为 " + status + "，仅已发布可弃用");
        }

        // 3. 赋值：状态流转
        this.status = ToolStatus.DEPRECATED;

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化
        toolRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.TOOL_DEPRECATED, operatorId);
    }

    /**
     * 重新发布：已废弃 → 已发布。
     * <p>
     * 仅切状态回 PUBLISHED，不重新解析端点元数据（弃用前的发布配置完整保留）。
     * 六步顺序：(1) 初始化 → (2) 校验 status==DEPRECATED → (3) status=PUBLISHED
     * → (4) 完整性校验（基础 + 形态）→ (5) 持久化 → (6) 发布 TOOL_PUBLISHED。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != DEPRECATED 时
     */
    public void republish(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：仅已废弃可重新发布
        Assert.notNull(this.status, "工具状态不能为空");
        if (this.status != ToolStatus.DEPRECATED) {
            throw new BusinessException(7007, "工具 " + num + " 当前状态为 " + status + "，仅已废弃可重新发布");
        }

        // 3. 赋值：状态流转
        this.status = ToolStatus.PUBLISHED;

        // 4. 领域完整性校验：基础 + 形态（保证废弃期间数据未被破坏）
        this.validate();
        this.validateByShape();

        // 5. 持久化
        toolRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.TOOL_PUBLISHED, operatorId);
    }

    /**
     * 删除草稿：仅 DRAFT 可删；infra deleteByNum 对草稿执行物理删除。
     * <p>
     * 已发布 / 已废弃禁删（须保留审计与挂载追溯）。
     * 六步顺序：(1) 初始化 → (2) 校验 status==DRAFT → (3)（无字段赋值）→ (4) 完整性校验
     * → (5) deleteByNum → (6) 发布 TOOL_DELETE_DRAFT。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != DRAFT 时
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：仅草稿态可硬删
        Assert.notNull(this.status, "工具状态不能为空");
        if (this.status != ToolStatus.DRAFT) {
            throw new BusinessException(7007, "工具 " + num + " 当前状态为 " + status + "，仅草稿态可删除");
        }

        // 3. 赋值：无（草稿物理删，不置 deleted 标识）

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化删除（infra 对草稿执行物理 DELETE）
        toolRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.TOOL_DELETE_DRAFT, operatorId);
    }

    // ---- 形态校验（仅发布路径调用） ----

    /**
     * 形态完整性校验：按 type + creationMode 校验各形态专有字段必填与约束。
     * <p>
     * 仅在 {@link #publish(String)} / {@link #republish(String)} 路径调用；草稿态允许形态字段缺失。
     *
     * @throws BusinessException 形态字段不满足约束时（7002~7005）
     */
    public void validateByShape() {
        // 发布时 description 必填（草稿态允许为空，但发布时必须完整）
        Assert.notBlank(description, "工具描述不能为空");
        switch (creationMode) {
            case REMOTE:
                validateMcpRemote();
                break;
            case API_PACKAGE:
                validateMcpApiPackage();
                break;
            case OPENAPI_SPEC:
                validateOpenApiSpecShape();
                break;
            case MANUAL:
                validateManualShape();
                break;
            default:
                throw new BusinessException(7001, "未知的工具创建方式：" + creationMode);
        }
    }

    // ---- 私有校验辅助 ----

    /** 校验 type 与 creationMode 组合是否合法。 */
    private void assertTypeModeMatch() {
        boolean match;
        switch (creationMode) {
            case REMOTE:
            case API_PACKAGE:
                match = type == ToolType.MCP;
                break;
            case OPENAPI_SPEC:
            case MANUAL:
                match = type == ToolType.FUNCTION_CALL;
                break;
            default:
                match = false;
        }
        if (!match) {
            throw new BusinessException(7001,
                    "工具类型 " + type + " 与创建方式 " + creationMode + " 组合不支持");
        }
    }

    /** MCP 远程连接形态校验：mcpConfigType + mcpConfig 必填且合法。 */
    private void validateMcpRemote() {
        if (mcpConfigType == null) {
            throw new BusinessException(7002, "MCP 配置子类型不能为空");
        }
        if (StrUtil.isBlank(mcpConfig)) {
            throw new BusinessException(7002, "MCP 配置不能为空");
        }
        if (mcpConfig.length() > MCP_CONFIG_MAX_LENGTH) {
            throw new BusinessException(7002, "MCP 配置大小不能超过 64KB");
        }
        // JSON 结构合法性（按 mcpConfigType 走不同 schema）由 infra GatewayImpl / 应用层做更细校验；
        // 领域层保证非空与大小上限。
    }

    /** MCP API 打包形态校验：packageMode 必填；按打包方式校验来源。 */
    private void validateMcpApiPackage() {
        if (packageMode == null) {
            throw new BusinessException(7003, "MCP API 打包方式不能为空");
        }
        if (packageMode == PackageMode.EXISTING_API) {
            if (StrUtil.isBlank(sourceFcToolNum)) {
                throw new BusinessException(7003, "已有 API 打包必须指定来源 FunctionCall 工具");
            }
        } else if (packageMode == PackageMode.OPENAPI_PASTE) {
            assertOpenApiSpecPresent();
        }
    }

    /** FC OpenAPI Spec 形态校验：openApiSpec 必填且合法。 */
    private void validateOpenApiSpecShape() {
        assertOpenApiSpecPresent();
    }

    /** FC 手动录入形态校验：baseUrl + endpoints + path 占位符一致性。 */
    private void validateManualShape() {
        if (StrUtil.isBlank(baseUrl)) {
            throw new BusinessException(7005, "Base URL 不能为空");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new BusinessException(7005, "Base URL 必须包含 http:// 或 https:// scheme");
        }
        if (CollUtil.isEmpty(endpoints)) {
            throw new BusinessException(7005, "至少需要一个 API 端点");
        }
        if (endpoints.size() > ENDPOINTS_MAX_COUNT) {
            throw new BusinessException(7005, "单个工具端点数不能超过 50 个");
        }
        for (ApiEndpoint endpoint : endpoints) {
            validateEndpoint(endpoint);
        }
    }

    /** 单端点校验：method / path / description 必填；path 占位符与 pathParams 一一对应。 */
    private void validateEndpoint(ApiEndpoint endpoint) {
        Assert.notNull(endpoint.getMethod(), "端点请求方式不能为空");
        Assert.notBlank(endpoint.getPath(), "端点 path 不能为空");
        if (!endpoint.getPath().startsWith("/")) {
            throw new BusinessException(7005, "端点 path 必须以 / 开头：" + endpoint.getPath());
        }
        Assert.notBlank(endpoint.getDescription(), "端点描述不能为空");

        // path 中的占位符集合
        Set<String> pathPlaceholders = new HashSet<>();
        var matcher = PATH_PLACEHOLDER.matcher(endpoint.getPath());
        while (matcher.find()) {
            pathPlaceholders.add(matcher.group(1));
        }
        // pathParams 声明的参数名集合
        Set<String> declaredPathParams = new HashSet<>();
        if (CollUtil.isNotEmpty(endpoint.getPathParams())) {
            for (ApiParam param : endpoint.getPathParams()) {
                Assert.notBlank(param.getName(), "Path 参数名不能为空");
                declaredPathParams.add(param.getName());
            }
        }
        // 一一对应校验
        for (String placeholder : pathPlaceholders) {
            if (!declaredPathParams.contains(placeholder)) {
                throw new BusinessException(7005,
                        "端点 " + endpoint.getPath() + " 中的占位符 {" + placeholder + "} 未在 Path 参数表定义");
            }
        }
        for (String declared : declaredPathParams) {
            if (!pathPlaceholders.contains(declared)) {
                throw new BusinessException(7005,
                        "Path 参数 " + declared + " 在端点 " + endpoint.getPath() + " 中未声明占位符");
            }
        }
    }

    /** openApiSpec 非空 + 大小上限校验（OPENAPI_SPEC / OPENAPI_PASTE 共用）。 */
    private void assertOpenApiSpecPresent() {
        if (StrUtil.isBlank(openApiSpec)) {
            throw new BusinessException(7004, "OpenAPI 规范文档不能为空");
        }
        if (openApiSpec.length() > OPENAPI_SPEC_MAX_LENGTH) {
            throw new BusinessException(7004, "OpenAPI 规范文档大小不能超过 1MB");
        }
    }

    /** 代理配置基础校验：未启用时不得有透传头；header 名去重（大小写不敏感）；变量占位符格式。 */
    private void validateProxy() {
        boolean enabled = Boolean.TRUE.equals(proxyEnabled);
        if (!enabled) {
            if (CollUtil.isNotEmpty(proxyHeaders)) {
                throw new BusinessException(7006, "未启用 MCP 代理时不能配置透传请求头");
            }
            return;
        }
        if (CollUtil.isEmpty(proxyHeaders)) {
            return;
        }
        if (proxyHeaders.size() > PROXY_HEADERS_MAX_COUNT) {
            throw new BusinessException(7006, "透传请求头数量不能超过 20 条");
        }
        Set<String> seenNames = new HashSet<>();
        for (ProxyHeader header : proxyHeaders) {
            Assert.notBlank(header.getName(), "透传请求头名不能为空");
            String lower = header.getName().toLowerCase();
            if (!seenNames.add(lower)) {
                throw new BusinessException(7006, "透传请求头名重复（大小写不敏感）：" + header.getName());
            }
            // 变量占位符格式校验：值中出现的每个 {...} 必须是 {字母数字下划线}
            String value = header.getValue();
            if (StrUtil.isNotBlank(value) && value.indexOf('{') >= 0) {
                var braceMatcher = Pattern.compile("\\{[^}]*}").matcher(value);
                while (braceMatcher.find()) {
                    String token = braceMatcher.group();
                    if (!VARIABLE_PLACEHOLDER.matcher(token).matches()) {
                        throw new BusinessException(7006,
                                "透传请求头变量占位符非法（仅允许 {字母数字下划线}）：" + token);
                    }
                }
            }
        }
    }

    // ---- 私有辅助 ----

    /** 是否为 OpenAPI 形态（发布时需解析端点元数据）。 */
    private boolean isOpenApiShape() {
        return creationMode == CreationMode.OPENAPI_SPEC
                || (creationMode == CreationMode.API_PACKAGE && packageMode == PackageMode.OPENAPI_PASTE);
    }

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
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(ToolDomainEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
