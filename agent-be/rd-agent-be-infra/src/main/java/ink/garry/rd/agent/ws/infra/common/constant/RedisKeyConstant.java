package ink.garry.rd.agent.ws.infra.common.constant;

/**
 * 跨领域 Redis 业务 key 前缀常量(非锁)。
 * <p>
 * 收敛散落在各处的 Redis key 拼接,与 {@link LockKeyConstant}(分布式锁前缀)分工:
 * 本类管"业务数据 key",后者管"互斥锁 key"。命名规则:{@code <domain>:<purpose>:},
 * 调用方在前缀后拼业务 ID。
 */
public final class RedisKeyConstant {

    /**
     * Agent→沙箱容器映射 key 前缀;拼接 agentNum 后存放该 Agent 当前复用的 sandboxId。
     * <p>
     * 沙箱容器按 <b>Agent 粒度</b>复用:同一 Agent 的所有会话共用一个远程容器(文件 / 已装依赖跨会话共享)。
     * 带 TTL(见 {@code SandboxProperties.ttlMinutes}),与容器自身 TTL 对齐;多副本下任意 pod
     * 凭此 id 重建句柄。
     */
    public static final String SANDBOX_AGENT_PREFIX = "sandbox:agent:";

    /**
     * 会话→execd session 映射 key 前缀;拼接 sessionNum 后存放 {@code sandboxId::execdSessionId}。
     * <p>
     * execd session 按 <b>会话粒度</b>开:同一容器内不同会话各持一个 bash session,
     * cd / 环境变量等 shell 状态互不干扰但共享文件系统。value 含 sandboxId 是为了校验——
     * Agent 容器 TTL 过期重建后 sandboxId 变化,旧 execd session 已失效,需检测到不一致后重建。
     */
    public static final String SANDBOX_SESSION_PREFIX = "sandbox:session:";

    /**
     * 鉴权权限并集缓存 key 前缀;拼接 {@code userId + ":" + workspaceNum} 后存放该用户在该空间下的权限码 Set。
     * <p>TTL 30 分钟(见 {@code AuthzCacheProperties.ttlMinutes});角色 / 绑定变更后由 listener evict
     * (本期为空实现,靠 TTL 自然过期)。</p>
     */
    public static final String AUTHZ_PERM_PREFIX = "authz:perm:";

    private RedisKeyConstant() {
    }
}
