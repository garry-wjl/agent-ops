package ink.garry.rd.agent.ws.infra.common.util;

/**
 * 工作空间上下文 ThreadLocal 持有器（仿 {@link UserContextHolder}）。
 * <p>
 * 由 adapter 层 WorkspaceContextInterceptor 在 preHandle 时 set，postHandle 时 clear。
 * 资产领域（Agent / Skill）的 Factory / QueryService 通过 {@link #currentWorkspaceNum()}
 * 读取当前活动空间编号，实现空间归属注入与查询过滤。
 */
public final class WorkspaceContextHolder {

    private static final ThreadLocal<WorkspaceContext> HOLDER = new ThreadLocal<>();

    private WorkspaceContextHolder() {}

    /**
     * 写入当前请求的工作空间上下文。
     *
     * @param ctx 工作空间上下文
     */
    public static void set(WorkspaceContext ctx) {
        HOLDER.set(ctx);
    }

    /**
     * 读取当前请求的工作空间上下文。
     *
     * @return 工作空间上下文；未设置时返回 null
     */
    public static WorkspaceContext get() {
        return HOLDER.get();
    }

    /**
     * 读取当前活动工作空间业务编号。
     *
     * @return 工作空间编号；上下文未设置时返回 null
     */
    public static String currentWorkspaceNum() {
        WorkspaceContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getWorkspaceNum();
    }

    /**
     * 读取调用者在当前空间内的角色。
     *
     * @return 角色（ADMIN / MEMBER）；上下文未设置时返回 null
     */
    public static String currentRole() {
        WorkspaceContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getRole();
    }

    /** 清理当前线程的工作空间上下文（请求结束时调用，避免线程复用泄漏）。 */
    public static void clear() {
        HOLDER.remove();
    }
}
