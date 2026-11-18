package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.agent.Agent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

/**
 * 请求结束时关闭本次构建的 HarnessAgent。
 * <p>
 * AgentScope 在 {@code ReActAgent} 构造时把实例登记进 JVM 全局
 * {@code GracefulShutdownManager}，只有 {@code close()} 才会注销。
 * 构建时已 {@code disableSubagents()}，这里只关闭当前这次调用的 Agent。
 */
@Slf4j
public final class HarnessAgentReleaser {

    private HarnessAgentReleaser() {
    }

    /**
     * 在 boundedElastic 上关闭，避免 {@code close()} 里的阻塞等待卡住 SSE 线程。
     * 调度失败时改为当前线程直接关闭。
     *
     * @param agent 本次调用装配出的 Agent；非 Harness 时直接返回
     */
    public static void releaseAsync(Agent agent) {
        if (!(agent instanceof AutoCloseable)) {
            return;
        }
        try {
            Schedulers.boundedElastic().schedule(() -> release(agent));
        } catch (RuntimeException e) {
            log.warn("异步关闭 HarnessAgent 调度失败，改为当前线程关闭: {}", e.toString());
            release(agent);
        }
    }

    /**
     * 同步关闭本次调用的 Agent。
     *
     * @param agent 本次调用装配出的 Agent；非 Harness 时直接返回
     */
    public static void release(Agent agent) {
        if (!(agent instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("关闭 HarnessAgent 失败 name={}: {}", safeName(agent), e.toString());
        }
    }

    private static String safeName(Agent agent) {
        try {
            return agent == null ? "unknown" : agent.getName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
