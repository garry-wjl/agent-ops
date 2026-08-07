package ink.garry.rd.agent.ws.domain.common;

/**
 * 跨领域的领域事件类型常量。
 * <p>
 * 命名严格遵循业务语义（过去式或动作完成态）。
 */
public final class DomainEventConstant {

    private DomainEventConstant() {}

    // ---- Agent 域 ----
    /** Agent 聚合首次创建并落库后触发。 */
    public static final String AGENT_CREATED = "AGENT_CREATED";
    /** Agent 元信息（名称、描述、负责人等）更新后触发。 */
    public static final String AGENT_UPDATED = "AGENT_UPDATED";
    /** Agent 被删除（软删除或下线归档）后触发。 */
    public static final String AGENT_DELETED = "AGENT_DELETED";
    /** Agent 被下线（停止对外提供服务）后触发。 */
    public static final String AGENT_OFFLINED = "AGENT_OFFLINED";
    /** Agent 草稿保存成功后触发。 */
    public static final String AGENT_DRAFT_SAVED = "AGENT_DRAFT_SAVED";
    /** Agent 草稿被丢弃（取消编辑）后触发。 */
    public static final String AGENT_DRAFT_DISCARDED = "AGENT_DRAFT_DISCARDED";
    /** Agent 新版本发布并落库成功后触发。 */
    public static final String AGENT_VERSION_PUBLISHED = "AGENT_VERSION_PUBLISHED";
    /** A2A Agent 完成一次 Nacos 同步（新增 / 元数据变更 / 实例下线 / 兜底轮询对账）后触发。 */
    public static final String AGENT_A2A_SYNCED = "AGENT_A2A_SYNCED";
    /** Agent 对外调用秘钥创建并落库后触发。 */
    public static final String AGENT_API_KEY_CREATED = "AGENT_API_KEY_CREATED";
    /** Agent 对外调用秘钥删除（软删除，认证立即失效）后触发。 */
    public static final String AGENT_API_KEY_DELETED = "AGENT_API_KEY_DELETED";
    /** Agent 对外调用秘钥被成功使用一次（刷新 lastUsedAt）后触发。 */
    public static final String AGENT_API_KEY_USED = "AGENT_API_KEY_USED";

    // ---- Skill 域（v2.5） ----
    /** Skill 主表保存成功后触发（首次创建或字段更新，统一发该事件，不区分 wasNew）。 */
    public static final String SKILL_SAVED = "SKILL_SAVED";
    /** Skill 删除（软删除）后触发。 */
    public static final String SKILL_DELETED = "SKILL_DELETED";
    /** Skill 新版本发布成功并将 Skill 切到 PUBLISHED 后触发。 */
    public static final String SKILL_VERSION_PUBLISHED = "SKILL_VERSION_PUBLISHED";
    /** Skill 回滚到历史版本（覆盖当前字段并置 DRAFT）后触发。 */
    public static final String SKILL_ROLLED_BACK = "SKILL_ROLLED_BACK";
    /** Skill 下架（PUBLISHED → DEPRECATED）后触发。 */
    public static final String SKILL_UNPUBLISHED = "SKILL_UNPUBLISHED";
    /** v2.7：SkillVersion 行自身状态由 DRAFT 切到 PUBLISHED 后触发（区别于 SKILL_VERSION_PUBLISHED 用于 Skill 聚合）。 */
    public static final String SKILL_VERSION_ACTIVATED = "SKILL_VERSION_ACTIVATED";
    /** v2.7：SkillVersion 行自身被下架（PUBLISHED → DEPRECATED）后触发。 */
    public static final String SKILL_VERSION_DEPRECATED = "SKILL_VERSION_DEPRECATED";
    /** v2.8：SkillVersion 行 save 成功后触发（首次 INSERT，状态默认 DRAFT）。 */
    public static final String SKILL_VERSION_SAVED = "SKILL_VERSION_SAVED";
    /** v2.8：SkillVersion 行 delete（软删除）后触发。 */
    public static final String SKILL_VERSION_DELETED = "SKILL_VERSION_DELETED";
    /** v3.0：Skill 提交发布进入检测（DRAFT → CHECKING）后触发。 */
    public static final String SKILL_CHECK_STARTED = "SKILL_CHECK_STARTED";
    /** v3.0：Skill 发布检测不通过（CHECKING → CHECK_FAILED）后触发，载荷含错误摘要。 */
    public static final String SKILL_CHECK_FAILED = "SKILL_CHECK_FAILED";

    // ---- SkillCheck 域（发布检测记录 v3.0） ----
    /** 一次 Skill 发布检测记录保存成功后触发（PASS 或 FAIL 均留痕）。 */
    public static final String SKILL_CHECK_RECORDED = "SKILL_CHECK_RECORDED";
    /** Skill 检测记录删除（软删除，清理场景）后触发。 */
    public static final String SKILL_CHECK_RECORD_DELETED = "SKILL_CHECK_RECORD_DELETED";


    // ---- Workspace 域 ----
    /** 工作空间聚合首次创建并落库后触发。 */
    public static final String WORKSPACE_CREATED = "WORKSPACE_CREATED";
    /** 工作空间编辑（名称 / 描述 / 成员任一变化）落库后触发。 */
    public static final String WORKSPACE_UPDATED = "WORKSPACE_UPDATED";
    /** 工作空间逻辑删除后触发。 */
    public static final String WORKSPACE_DELETED = "WORKSPACE_DELETED";

    // ---- Sandbox 域 ----
    /** 沙箱聚合首次创建并落库后触发。 */
    public static final String SANDBOX_CREATED = "SANDBOX_CREATED";
    /** 沙箱编辑（名称 / 规格 / 备注任一变化）落库后触发。 */
    public static final String SANDBOX_UPDATED = "SANDBOX_UPDATED";
    /** 沙箱提交（草稿 / 失败 → 初始化）后触发；由监听器驱动异步供给容器。 */
    public static final String SANDBOX_SUBMITTED = "SANDBOX_SUBMITTED";
    /** 沙箱上线（初始化 → 在线，容器就绪）后触发。 */
    public static final String SANDBOX_ONLINED = "SANDBOX_ONLINED";
    /** 沙箱下线（在线 → 下线，容器已释放）后触发。 */
    public static final String SANDBOX_OFFLINED = "SANDBOX_OFFLINED";
    /** 沙箱初始化失败（初始化 → 失败）后触发，载荷含失败原因。 */
    public static final String SANDBOX_PROVISION_FAILED = "SANDBOX_PROVISION_FAILED";
    /** 沙箱逻辑删除后触发。 */
    public static final String SANDBOX_DELETED = "SANDBOX_DELETED";

    // ---- Session/Invoke 域（占位） ----
    /** 会话（Session）创建后触发。 */
    public static final String SESSION_CREATED = "SESSION_CREATED";
    /** 会话（Session）删除（含级联）后触发。 */
    public static final String SESSION_DELETED = "SESSION_DELETED";
    /** 单条消息（Message）保存（创建或更新）后触发。 */
    public static final String MESSAGE_SAVED = "MESSAGE_SAVED";
    /** 单条消息（Message）删除后触发。 */
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
    /** 一次 Agent/Skill 调用执行完成后触发。 */
    public static final String INVOCATION_FINISHED = "INVOCATION_FINISHED";

    // ---- Evaluation 域（占位） ----
    /** 评估任务执行完成（成功或失败终态）后触发。 */
    public static final String EVALUATION_FINISHED = "EVALUATION_FINISHED";

    // ---- Tool 域（工具管理 v1.0） ----
    /** 工具主表保存成功后触发（首次创建或字段编辑，统一发该事件，不区分 wasNew；审计映射 TOOL_CREATE）。 */
    public static final String TOOL_SAVED = "TOOL_SAVED";
    /** 工具发布（草稿 → 已发布）或重新发布（已废弃 → 已发布）成功后触发（审计映射 TOOL_PUBLISH）。 */
    public static final String TOOL_PUBLISHED = "TOOL_PUBLISHED";
    /** 工具弃用（已发布 → 已废弃）后触发（审计映射 TOOL_DEPRECATE）。 */
    public static final String TOOL_DEPRECATED = "TOOL_DEPRECATED";
    /** 工具草稿删除（物理删）后触发（审计映射 TOOL_DELETE_DRAFT）。 */
    public static final String TOOL_DELETE_DRAFT = "TOOL_DELETE_DRAFT";

    // ---- Prompt 域（Prompt 中心 v1.0） ----
    /** Prompt 保存成功后触发（首次创建或字段编辑，统一发该事件，不区分 wasNew）。 */
    public static final String PROMPT_SAVED = "PROMPT_SAVED";
    /** Prompt 删除（软删除）后触发。 */
    public static final String PROMPT_DELETED = "PROMPT_DELETED";

    // ---- Model 域（模型管理 v1.0） ----
    /** 模型保存成功后触发（首次创建或字段编辑，统一发该事件，不区分 wasNew；载荷不含 apiKey）。 */
    public static final String MODEL_SAVED = "MODEL_SAVED";
    /** 模型启用（草稿 / 禁用 → 启用）后触发。 */
    public static final String MODEL_ENABLED = "MODEL_ENABLED";
    /** 模型禁用（启用 → 禁用）后触发。 */
    public static final String MODEL_DISABLED = "MODEL_DISABLED";
    /** 模型删除（软删除，仅草稿态）后触发。 */
    public static final String MODEL_DELETED = "MODEL_DELETED";

    // ---- Authz 域（权限管理 v1.0） ----
    /** Role 聚合首次落库后触发（载荷含 roleNum / name / scope / workspaceNum / permissionCodes）。 */
    public static final String ROLE_CREATED = "ROLE_CREATED";
    /** Role 聚合非首次 save 落库后触发（含 permissionCodes / name / description 等变更）。 */
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    /** Role 聚合软删除后触发。 */
    public static final String ROLE_DELETED = "ROLE_DELETED";
    /** 单用户在某空间被绑定 / 追加角色后触发（每变更用户一条）。 */
    public static final String USER_ROLE_BOUND = "USER_ROLE_BOUND";
    /** 单用户在某空间被解除全部 / 部分角色后触发（每变更用户一条）。 */
    public static final String USER_ROLE_UNBOUND = "USER_ROLE_UNBOUND";
}
