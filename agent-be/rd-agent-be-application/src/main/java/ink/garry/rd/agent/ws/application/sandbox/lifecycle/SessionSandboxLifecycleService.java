package ink.garry.rd.agent.ws.application.sandbox.lifecycle;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 会话级沙箱状态机：后台预热静默推进；用户真正用沙箱时再向订阅者推送 {@link SandboxStatusEvent}。
 */
@Slf4j
@Service
public class SessionSandboxLifecycleService {

    @Resource
    private SandboxPoolService sandboxPoolService;

    private final ConcurrentHashMap<String, SessionState> states = new ConcurrentHashMap<>();

    /**
     * 订阅某会话的状态推送（仅本次 invoke 应订阅；预热无订阅则不打扰 UI）。
     *
     * @param sessionNum 会话编号
     * @return 热流，取消订阅即停止接收
     */
    public Flux<SandboxStatusEvent> subscribe(String sessionNum) {
        if (StrUtil.isBlank(sessionNum)) {
            return Flux.empty();
        }
        Sinks.Many<SandboxStatusEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        SessionState st = states.computeIfAbsent(sessionNum, SessionState::new);
        Consumer<SandboxStatusEvent> listener = sink::tryEmitNext;
        st.listeners.add(listener);
        return sink.asFlux()
                .doFinally(sig -> st.listeners.remove(listener));
    }

    /**
     * 当前阶段快照。
     *
     * @param sessionNum 会话编号
     * @return 阶段，未知会话视为 IDLE
     */
    public SessionSandboxPhase phaseOf(String sessionNum) {
        if (StrUtil.isBlank(sessionNum)) {
            return SessionSandboxPhase.IDLE;
        }
        SessionState st = states.get(sessionNum);
        return st == null ? SessionSandboxPhase.IDLE : st.phase;
    }

    /**
     * 已就绪的 instanceId；未就绪返回 null。
     *
     * @param sessionNum 会话编号
     * @return instanceId 或 null
     */
    public String readyInstanceId(String sessionNum) {
        if (StrUtil.isBlank(sessionNum)) {
            return null;
        }
        SessionState st = states.get(sessionNum);
        if (st == null || st.phase != SessionSandboxPhase.READY) {
            return null;
        }
        return st.instanceId;
    }

    /**
     * 后台预热：推进状态机但不要求有 SSE 订阅者。
     *
     * @param sandboxNum 资产编号
     * @param sessionNum 会话编号
     * @param operatorId 操作人
     * @param agentNum   Agent 编号
     * @return instanceId
     */
    public String ensureBoundSilent(String sandboxNum, String sessionNum,
                                     String operatorId, String agentNum) {
        return ensureBoundInternal(sandboxNum, sessionNum, operatorId, agentNum, false);
    }

    /**
     * 用户路径（工具执行）：若尚未 READY，向订阅者发 PREPARING，阻塞至 Ready/失败。
     *
     * @param preferredInstanceId 调用方持有的 id（可空）
     * @param sandboxNum          资产编号
     * @param sessionNum          会话编号
     * @param agentNum            Agent 编号
     * @param operatorId          操作人
     * @return 可用 instanceId
     */
    public String ensureAliveOrRebindForUse(String sessionNum,
                                              String preferredInstanceId,
                                              String sandboxNum,
                                              String agentNum,
                                              String operatorId) {
        if (StrUtil.isBlank(sessionNum)) {
            throw new IllegalArgumentException("sessionNum 不能为空");
        }
        SessionState st = states.computeIfAbsent(sessionNum, SessionState::new);
        if (st.phase == SessionSandboxPhase.READY && StrUtil.isNotBlank(st.instanceId)) {
            // 已就绪：直接复用池内探活/续期，不发 PREPARING
            return sandboxPoolService.ensureAliveOrRebind(
                    sessionNum, preferredInstanceId, sandboxNum, agentNum, operatorId);
        }
        // 未就绪：通知 UI 后走 ensure（可能与预热 in-flight 合并）
        markPreparing(sessionNum, true);
        try {
            String id = sandboxPoolService.ensureAliveOrRebind(
                    sessionNum, preferredInstanceId, sandboxNum, agentNum, operatorId);
            markReady(sessionNum, id, true);
            return id;
        } catch (RuntimeException e) {
            markFailed(sessionNum, e.getMessage(), true);
            throw e;
        }
    }

    private String ensureBoundInternal(String sandboxNum, String sessionNum,
                                         String operatorId, String agentNum, boolean notify) {
        Objects.requireNonNull(sessionNum, "sessionNum");
        markPreparing(sessionNum, notify);
        try {
            String id = sandboxPoolService.ensureBound(sandboxNum, sessionNum, operatorId, agentNum);
            markReady(sessionNum, id, notify);
            return id;
        } catch (RuntimeException e) {
            markFailed(sessionNum, e.getMessage(), notify);
            throw e;
        }
    }

    private void markPreparing(String sessionNum, boolean notify) {
        SessionState st = states.computeIfAbsent(sessionNum, SessionState::new);
        synchronized (st) {
            if (st.phase == SessionSandboxPhase.READY) {
                return;
            }
            st.phase = SessionSandboxPhase.PREPARING;
            st.message = null;
            if (st.readyFuture == null || st.readyFuture.isDone()) {
                st.readyFuture = new CompletableFuture<>();
            }
        }
        if (notify) {
            emit(sessionNum, SessionSandboxPhase.PREPARING, null, null);
        }
    }

    private void markReady(String sessionNum, String instanceId, boolean notify) {
        SessionState st = states.computeIfAbsent(sessionNum, SessionState::new);
        synchronized (st) {
            st.phase = SessionSandboxPhase.READY;
            st.instanceId = instanceId;
            st.message = null;
            if (st.readyFuture != null && !st.readyFuture.isDone()) {
                st.readyFuture.complete(instanceId);
            }
        }
        if (notify) {
            emit(sessionNum, SessionSandboxPhase.READY, instanceId, null);
        }
        log.debug("[sandbox-lifecycle] READY sessionNum={} instanceId={} notify={}",
                sessionNum, instanceId, notify);
    }

    private void markFailed(String sessionNum, String message, boolean notify) {
        SessionState st = states.computeIfAbsent(sessionNum, SessionState::new);
        synchronized (st) {
            st.phase = SessionSandboxPhase.FAILED;
            st.message = message;
            if (st.readyFuture != null && !st.readyFuture.isDone()) {
                st.readyFuture.completeExceptionally(
                        new IllegalStateException(StrUtil.blankToDefault(message, "sandbox failed")));
            }
        }
        if (notify) {
            emit(sessionNum, SessionSandboxPhase.FAILED, null, message);
        }
        log.warn("[sandbox-lifecycle] FAILED sessionNum={} msg={}", sessionNum, message);
    }

    private void emit(String sessionNum, SessionSandboxPhase phase,
                      String instanceId, String message) {
        SessionState st = states.get(sessionNum);
        if (st == null || st.listeners.isEmpty()) {
            return;
        }
        SandboxStatusEvent event = SandboxStatusEvent.builder()
                .sessionNum(sessionNum)
                .phase(phase.name())
                .instanceId(instanceId)
                .message(message)
                .ts(System.currentTimeMillis())
                .build();
        for (Consumer<SandboxStatusEvent> listener : st.listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("[sandbox-lifecycle] listener failed sessionNum={}: {}",
                        sessionNum, e.getMessage());
            }
        }
    }

    /**
     * 测试 / 运维：清除会话状态。
     *
     * @param sessionNum 会话编号
     */
    public void clear(String sessionNum) {
        if (StrUtil.isNotBlank(sessionNum)) {
            states.remove(sessionNum);
        }
    }

    /** @return 内部状态表（单测） */
    Map<String, SessionState> debugStates() {
        return states;
    }

    static final class SessionState {
        final String sessionNum;
        volatile SessionSandboxPhase phase = SessionSandboxPhase.IDLE;
        volatile String instanceId;
        volatile String message;
        volatile CompletableFuture<String> readyFuture;
        final List<Consumer<SandboxStatusEvent>> listeners = new CopyOnWriteArrayList<>();

        SessionState(String sessionNum) {
            this.sessionNum = sessionNum;
        }

        String awaitReady(long seconds) {
            CompletableFuture<String> f;
            synchronized (this) {
                if (phase == SessionSandboxPhase.READY && StrUtil.isNotBlank(instanceId)) {
                    return instanceId;
                }
                if (readyFuture == null) {
                    readyFuture = new CompletableFuture<>();
                }
                f = readyFuture;
            }
            try {
                return f.get(seconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("等待沙箱就绪超时或失败: " + e.getMessage(), e);
            }
        }
    }
}
