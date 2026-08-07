package ink.garry.rd.agent.ws.infra.common.util;

import org.slf4j.MDC;

/**
 * 当前请求 trace_id 上下文（基于 MDC + ThreadLocal）。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";
    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {}

    public static void set(String traceId) {
        HOLDER.set(traceId);
        MDC.put(MDC_KEY, traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_KEY);
    }
}
