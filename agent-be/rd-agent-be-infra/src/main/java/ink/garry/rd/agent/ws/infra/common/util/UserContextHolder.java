package ink.garry.rd.agent.ws.infra.common.util;

/**
 * 用户上下文 ThreadLocal 持有器。
 * <p>
 * 由 adapter 层 UserContextFilter 在请求开始时 set，结束时 clear。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static String currentUserId() {
        UserContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
