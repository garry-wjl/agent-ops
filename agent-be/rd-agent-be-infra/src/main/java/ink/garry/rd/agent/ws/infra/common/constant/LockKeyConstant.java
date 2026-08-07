package ink.garry.rd.agent.ws.infra.common.constant;

/**
 * 跨领域分布式锁键前缀常量(Redisson)。
 * <p>
 * 收敛跨基础设施使用的 lock key,避免各 Gateway 实现自行定义前缀导致冲突或不一致。
 * 命名规则:{@code <domain>:<aggregate>:<purpose>:} —— 调用方在前缀后拼业务编号。
 */
public final class LockKeyConstant {

    /** Agent 草稿编辑互斥锁前缀;拼接 agentNum 后定位单 Agent 草稿临界区 */
    public static final String AGENT_DRAFT_LOCK_PREFIX = "agent:draft:lock:";

    /**
     * Agent 用例编排互斥锁前缀;拼接 agentNum 后定位单 Agent 的应用层业务用例临界区。
     * <p>
     * 用途:application 层在 createVersion / editDraftVersion / publish / offline 等多步编排
     * (加载 + 校验 + 状态流转 + 持久化 + 事件)前抢锁,防止用户并发 / 重试触发状态机错乱;
     * 守护粒度比草稿编辑锁 {@link #AGENT_DRAFT_LOCK_PREFIX} 更粗(覆盖整条跨 Repository + 事件用例)。
     */
    public static final String AGENT_COMMAND_LOCK_PREFIX = "agent:command:lock:";

    /**
     * Agent 创建用例互斥锁前缀;拼接 {@code workspaceNum + ":" + name} 后定位单 (空间, 名称) 创建临界区。
     * <p>
     * 用途:create 阶段 Agent num 尚未生成,无法走 {@link #AGENT_COMMAND_LOCK_PREFIX};
     * 按业务唯一组合 (workspaceNum, name) 加锁,防止连点 / 重试创建出多条同空间同名 Agent
     * (V33 后已下线 uq_agent_ws_name DDL 约束,本锁 + AgentQueryService.existsByWorkspaceAndName
     * 是同空间唯一的唯一兜底,见 V33__drop_uq_agent_ws_name.sql)。
     */
    public static final String AGENT_CREATE_LOCK_PREFIX = "agent:create:lock:";

    /**
     * Agent 对外调用秘钥创建互斥锁前缀;拼接 agentNum 后定位单 Agent 的秘钥创建临界区。
     * <p>
     * 用途:create 阶段秘钥 num 尚未生成,无法按 keyNum 加锁;按 agentNum 串行化同一 Agent 的并发创建,
     * 在"≤50 count 校验"之外提前防止 TOCTOU 竞态突破上限(两个并发请求都读到 49 → 都通过 → 变 51)。
     */
    public static final String AGENT_API_KEY_CREATE_LOCK_PREFIX = "agent:apikey:create:lock:";

    /**
     * Skill 保存互斥锁前缀;拼接 Skill num 后定位单 Skill 写入临界区。
     * <p>
     * 用途:防止用户重试 / 并发请求导致同一 num 被多次 insert(在 num 唯一索引兜底之外
     * 提前拒绝,避免唯一冲突异常打到 GlobalExceptionHandler)。
     */
    public static final String SKILL_SAVE_LOCK_PREFIX = "skill:save:lock:";

    /**
     * SkillVersion 保存互斥锁前缀;拼接 SkillVersion num 后定位单版本写入临界区。
     * <p>
     * 用途:同 {@link #SKILL_SAVE_LOCK_PREFIX},粒度按 SkillVersion num,避免与 Skill 主表
     * 写互斥过粗(同一 Skill 下不同版本的写操作可并行)。
     */
    public static final String SKILL_VERSION_SAVE_LOCK_PREFIX = "skill:version:save:lock:";

    /**
     * Skill 用例编排互斥锁前缀;拼接 Skill num 后定位单 Skill 的应用层业务用例临界区。
     * <p>
     * 用途:application 层在 update/publish/rollback 等多步编排前抢锁,防止用户并发 / 重试
     * 触发状态机错乱(如同时 publish 和 rollback);与仓储层 {@link #SKILL_SAVE_LOCK_PREFIX}
     * 互为兜底,守护粒度更粗(覆盖跨 Repository + 事件发布的整条用例)。
     */
    public static final String SKILL_COMMAND_LOCK_PREFIX = "skill:command:lock:";

    /**
     * Skill 创建用例互斥锁前缀;拼接 {@code ownerUserId + ":" + name} 后定位单 (owner, name)
     * 组合的创建临界区。
     * <p>
     * 用途:createSkill 用例阶段 Skill num 尚未生成,无法走 {@link #SKILL_COMMAND_LOCK_PREFIX};
     * 按业务唯一组合 (ownerUserId, name) 加锁,防止用户连点 / 重试创建出多条同名 Skill
     * (作 name 唯一索引兜底之外的提前拒绝)。
     */
    public static final String SKILL_CREATE_LOCK_PREFIX = "skill:create:lock:";

    /**
     * Workspace 保存互斥锁前缀;拼接 Workspace num 后定位单空间写入临界区。
     * <p>
     * 用途:同 {@link #SKILL_SAVE_LOCK_PREFIX} —— save 与 deleteByNum 共用同一把锁,防止
     * 重试 / 并发请求在 num 唯一索引兜底之前先撞 insert,或 "保存 + 删除" 并发产生竞态。
     */
    public static final String WORKSPACE_SAVE_LOCK_PREFIX = "workspace:save:lock:";

    /**
     * Workspace 用例编排互斥锁前缀;拼接 Workspace num 后定位单空间的应用层业务用例临界区。
     * <p>
     * 用途:application 层 update / delete 等多步编排(加载 + 校验 + set + save / 资产计数)前抢锁,
     * 防止并发 / 重试触发竞态;与仓储层 {@link #WORKSPACE_SAVE_LOCK_PREFIX} 互为兜底,守护粒度更粗。
     */
    public static final String WORKSPACE_COMMAND_LOCK_PREFIX = "workspace:command:lock:";

    /**
     * Workspace 创建用例互斥锁前缀;拼接 {@code createNo + ":" + name} 后定位单 (创建人, 名称)
     * 组合的创建临界区。
     * <p>
     * 用途:createWorkspace 阶段 num 尚未生成,无法走 {@link #WORKSPACE_COMMAND_LOCK_PREFIX};
     * 按业务唯一组合 (createNo, name) 加锁,防止连点 / 重试创建出多条同名空间
     * (作 uq_workspace_creator_name 唯一索引兜底之外的提前拒绝)。
     */
    public static final String WORKSPACE_CREATE_LOCK_PREFIX = "workspace:create:lock:";

    /**
     * Agent 级沙箱容器首建互斥锁前缀;拼接 agentNum 后定位单 Agent 的沙箱容器创建临界区。
     * <p>
     * 用途:application 层 obtainContainerForAgent 在"查映射未命中 → 新建容器 → 写映射"前抢锁,
     * 防止同一 Agent 的并发请求各自创建出多个远程容器(在映射 double-check 之外提前串行化),
     * 保证一个 Agent 只对应一个沙箱容器。
     */
    public static final String SANDBOX_AGENT_LOCK_PREFIX = "sandbox:agent:lock:";

    /**
     * 会话级 execd session 首建互斥锁前缀;拼接 sessionNum 后定位单会话的 execd session 创建临界区。
     * <p>
     * 用途:obtainSessionForRun 在"查 session 映射未命中 → createSession → 写映射"前抢锁,
     * 防止同一会话并发请求在同一容器内开出多个 bash session。
     */
    public static final String SANDBOX_SESSION_LOCK_PREFIX = "sandbox:session:lock:";

    /**
     * 沙箱资产仓储写互斥锁前缀;拼接沙箱 num 后守护 {@code SandboxRepositoryImpl} 的
     * save / deleteByNum 临界区,防止前端重试 / 并发请求在 num 唯一索引兜底前先撞 insert。
     */
    public static final String SANDBOX_SAVE_LOCK_PREFIX = "sandbox:save:lock:";

    /**
     * 沙箱资产用例级互斥锁前缀;拼接沙箱 num 后守护 {@code SandboxCommandService} 的单沙箱命令用例
     * (编辑 / 提交 / 上线 / 下线 / 重新上线 / 删除 / 状态回写),与仓储层 {@link #SANDBOX_SAVE_LOCK_PREFIX}
     * 互为兜底,守护粒度更粗。
     */
    public static final String SANDBOX_COMMAND_LOCK_PREFIX = "sandbox:command:lock:";

    /**
     * 角色创建用例互斥锁前缀;拼接 {@code workspaceNum + ":" + name} 后定位单 (空间, 名称) 角色创建临界区。
     * <p>用途:create 阶段 roleNum 尚未生成,无法按 roleNum 加锁;按业务唯一组合提前拒绝并发同名创建。
     */
    public static final String AUTHZ_ROLE_CREATE_LOCK_PREFIX = "authz:role:create:lock:";

    /**
     * 角色编辑用例互斥锁前缀;拼接 roleNum 后定位单角色编辑临界区。
     */
    public static final String AUTHZ_ROLE_UPDATE_LOCK_PREFIX = "authz:role:update:lock:";

    /**
     * 角色删除用例互斥锁前缀;拼接 roleNum 后定位单角色删除临界区。
     */
    public static final String AUTHZ_ROLE_DELETE_LOCK_PREFIX = "authz:role:delete:lock:";

    /**
     * 整空间用户-角色绑定互斥锁前缀;拼接 workspaceNum 后定位单空间整聚合覆盖写临界区。
     */
    public static final String AUTHZ_ASSIGNMENT_LOCK_PREFIX = "authz:assignment:lock:";

    /**
     * 沙箱新建用例锁前缀;拼接 "工作空间编号:名称" 后守护 createSandbox 临界区。
     * <p>
     * 用途:createSandbox 阶段 num 尚未生成,无法走 {@link #SANDBOX_COMMAND_LOCK_PREFIX};
     * 以 (workspaceNum, name) 维度抢锁,防止连点 / 重试创建同名沙箱。
     */
    public static final String SANDBOX_CREATE_LOCK_PREFIX = "sandbox:create:lock:";

    /**
     * 沙箱脏态对账全局锁；多副本部署下保证同一时刻只有一个实例执行
     * {@code SandboxReconcileScheduler} 的对账任务，避免并发重复 kill / 校正。
     */
    public static final String SANDBOX_RECONCILE_LOCK = "sandbox:reconcile:lock";

    /**
     * A2A PENDING_SYNC 兜底对账全局锁；多副本部署下保证同一时刻只有一个实例执行
     * {@code A2aSyncScheduler.syncPending} 的对账任务，避免重复 fetch Nacos、重复写状态。
     */
    public static final String A2A_SYNC_RECONCILE_LOCK = "a2a:sync:reconcile:lock";

    /**
     * 工具资产仓储写互斥锁前缀;拼接工具 num 后守护 {@code ToolRepositoryImpl} 的
     * save / deleteByNum 临界区,防止前端重试 / 并发请求在 num 唯一索引兜底前先撞 insert。
     */
    public static final String TOOL_SAVE_LOCK_PREFIX = "tool:save:lock:";

    /**
     * 工具资产用例级互斥锁前缀;拼接工具 num 后守护 {@code ToolCommandService} 的单工具命令用例
     * (编辑 / 发布 / 弃用 / 重新发布 / 删除),与仓储层 {@link #TOOL_SAVE_LOCK_PREFIX}
     * 互为兜底,守护粒度更粗(覆盖跨 Repository + 事件发布的整条用例)。
     */
    public static final String TOOL_COMMAND_LOCK_PREFIX = "tool:command:lock:";

    /**
     * 工具新建用例锁前缀;拼接 "工作空间编号:名称" 后守护 createTool 临界区。
     * <p>
     * 用途:createTool 阶段 num 尚未生成,无法走 {@link #TOOL_COMMAND_LOCK_PREFIX};
     * 以 (workspaceNum, name) 维度抢锁,防止连点 / 重试创建同名工具
     * (作 uq_tool_ws_name 唯一索引兜底之外的提前拒绝)。
     */
    public static final String TOOL_CREATE_LOCK_PREFIX = "tool:create:lock:";

    /**
     * Prompt 资产仓储写互斥锁前缀;拼接 Prompt num 后守护 {@code PromptRepositoryImpl} 的
     * save / deleteByNum 临界区,防止前端重试 / 并发请求在 num 唯一索引兜底前先撞 insert。
     */
    public static final String PROMPT_SAVE_LOCK_PREFIX = "prompt:save:lock:";

    /**
     * Prompt 资产用例级互斥锁前缀;拼接 Prompt num 后守护 {@code PromptCommandService} 的
     * 单条命令用例(编辑 / 删除),与仓储层 {@link #PROMPT_SAVE_LOCK_PREFIX} 互为兜底,
     * 守护粒度更粗(覆盖跨 Repository + 事件发布的整条用例)。
     */
    public static final String PROMPT_COMMAND_LOCK_PREFIX = "prompt:command:lock:";

    /**
     * Prompt 新建用例锁前缀;拼接 "工作空间编号:promptKey" 后守护 createPrompt 临界区。
     * <p>
     * 用途:createPrompt 阶段 num 尚未生成,无法走 {@link #PROMPT_COMMAND_LOCK_PREFIX};
     * 以 (workspaceNum, promptKey) 维度抢锁,防止连点 / 重试创建同 key Prompt
     * (作 uq_prompt_ws_key 唯一索引兜底之外的提前拒绝)。
     */
    public static final String PROMPT_CREATE_LOCK_PREFIX = "prompt:create:lock:";

    /**
     * 模型资产仓储写互斥锁前缀;拼接模型 num 后守护 {@code ModelRepositoryImpl} 的
     * save / deleteByNum 临界区,防止前端重试 / 并发请求在 num 唯一索引兜底前先撞 insert。
     */
    public static final String MODEL_SAVE_LOCK_PREFIX = "model:save:lock:";

    /**
     * 模型资产用例级互斥锁前缀;拼接模型 num 后守护 {@code ModelCommandService} 的
     * 单条命令用例(编辑 / 启用 / 禁用 / 删除),与仓储层 {@link #MODEL_SAVE_LOCK_PREFIX} 互为兜底,
     * 守护粒度更粗(覆盖跨 Repository + 事件发布的整条用例)。
     */
    public static final String MODEL_COMMAND_LOCK_PREFIX = "model:command:lock:";

    /**
     * 模型新建用例锁前缀;拼接 "工作空间编号:modelId" 后守护 createModel 临界区。
     * <p>
     * 用途:createModel 阶段 num 尚未生成,无法走 {@link #MODEL_COMMAND_LOCK_PREFIX};
     * 以 (workspaceNum, modelId) 维度抢锁,防止连点 / 重试创建同 modelId 模型
     * (作 uq_model_ws_model_id 唯一索引兜底之外的提前拒绝)。
     */
    public static final String MODEL_CREATE_LOCK_PREFIX = "model:create:lock:";


    /** 用户写用例互斥锁前缀；拼接用户 num 后守护 UserCommandService 单条命令。 */
    public static final String USER_COMMAND_LOCK_PREFIX = "user:command:lock:";

    /** 用户新建用例锁前缀；拼接 username 后守护 createUser 临界区。 */
    public static final String USER_CREATE_LOCK_PREFIX = "user:create:lock:";

    private LockKeyConstant() {
    }
}
