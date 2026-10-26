package ink.garry.rd.agent.ws.application.agentrunner.run;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Agent invoke 编排：开跑与 HTTP 订阅解耦，事件写入 Redis Stream，支持 lastEventId 续传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunCoordinator {

    private final AgentRunEventStore eventStore;
    private final AgentRunnerService agentRunnerService;
    private final SessionCommandService sessionCommandService;
    private final ObjectMapper objectMapper;

    @Value("${app.agent-run.poll-interval:200ms}")
    private Duration pollInterval;

    /**
     * 开跑或续传。
     *
     * @param resume       显式续传
     * @param lastEventId  {@code runId:seq}，可空
     * @param runIdHint    可选 runId（续传更稳）
     */
    public Flux<AgentRunStreamEvent> invokeOrResume(
            boolean resume,
            String lastEventId,
            String runIdHint,
            String agentNum,
            NormalizedInvokeContent content,
            String sessionNum,
            String operatorId,
            String targetVersion,
            String origin,
            Map<String, Object> context) {

        boolean resumeMode = resume
                || (StrUtil.isNotBlank(lastEventId) && isBlankContent(content));
        if (resumeMode) {
            return resume(sessionNum, runIdHint, lastEventId);
        }
        if (isBlankContent(content)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "开跑时 input/attachments 不能同时为空");
        }
        return start(agentNum, content, sessionNum, operatorId, targetVersion, origin, context);
    }

    /**
     * 纯续传：从 lastEventId 之后重放并跟随 live。
     */
    public Flux<AgentRunStreamEvent> resume(String sessionNum, String runIdHint, String lastEventId) {
        ResolvedCursor cursor = resolveCursor(sessionNum, runIdHint, lastEventId);
        AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(cursor.runId())
                .orElseThrow(() -> new BusinessException(
                        BizCode.RUN_NOT_FOUND.getCode(), BizCode.RUN_NOT_FOUND.getMessage()));

        if (meta.status().terminal() && meta.lastSeq() <= cursor.afterSeq()) {
            // 已结束且客户端已收齐：补一条 run_finished 提示
            return Flux.just(finishedEvent(cursor.runId(), meta.lastSeq() + 1, meta.status()));
        }
        return subscribe(cursor.runId(), cursor.afterSeq());
    }

    private Flux<AgentRunStreamEvent> start(
            String agentNum,
            NormalizedInvokeContent content,
            String sessionNum,
            String operatorId,
            String targetVersion,
            String origin,
            Map<String, Object> context) {

        String ensuredSession = ensureSession(agentNum, sessionNum, operatorId, origin, context);
        eventStore.findRunningRunId(ensuredSession).ifPresent(busy -> {
            throw new BusinessException(BizCode.SESSION_BUSY.getCode(),
                    BizCode.SESSION_BUSY.getMessage() + " runId=" + busy);
        });

        String runId = "RUN" + IdUtil.getSnowflakeNextIdStr();
        eventStore.createRun(runId, ensuredSession, agentNum);

        AtomicLong seq = new AtomicLong(0);
        long startedSeq = seq.incrementAndGet();
        eventStore.append(runId, startedSeq, "run_started",
                writeJson(Map.of(
                        "runId", runId,
                        "sessionNum", ensuredSession,
                        "agentNum", agentNum,
                        "status", AgentRunStatus.RUNNING.name())));

        // runAgentFrames 在返回 Flux 前会同步 build Agent（可能秒级～分钟级）。
        // 若在 HTTP 线程上调用，SSE 响应头迟迟不发出，网关/ALB 会回 504（前端见「SSE HTTP 504」）。
        // 先 return subscribe（客户端立刻收到 run_started），装配与出帧丢到 boundedElastic。
        launchAgentRunAsync(runId, seq, agentNum, content, ensuredSession, operatorId,
                targetVersion, origin, context);
        return subscribe(runId, 0L);
    }

    /**
     * 异步启动 agent 出帧写入事件库。
     * <p>
     * 在请求线程抓取 {@link WorkspaceContext} 与 {@link RequestAttributes}，装配线程绑定后再调用
     * {@code runAgentFrames}，以免跨线程丢失工作空间与入站头。装配返回并完成订阅登记后，在
     * {@code finally} 中清理两者，避免线程池复用串请求。
     */
    private void launchAgentRunAsync(
            String runId,
            AtomicLong seq,
            String agentNum,
            NormalizedInvokeContent content,
            String sessionNum,
            String operatorId,
            String targetVersion,
            String origin,
            Map<String, Object> context) {
        WorkspaceContext workspaceContext = WorkspaceContextHolder.get();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Mono.fromRunnable(() -> {
                    try {
                        if (workspaceContext != null) {
                            WorkspaceContextHolder.set(workspaceContext);
                        }
                        if (requestAttributes != null) {
                            RequestContextHolder.setRequestAttributes(requestAttributes);
                        }
                        Flux<AgentInvokeFrame> frames = agentRunnerService.runAgentFrames(
                                agentNum, content, sessionNum, operatorId, targetVersion,
                                origin, context, runId);
                        // 与 HTTP 解耦：独立订阅，客户端断开不停跑
                        frames.concatMap(frame -> Mono.fromCallable(() -> {
                                    long s = seq.incrementAndGet();
                                    SerializedFrame sf = serializeFrame(frame);
                                    eventStore.append(runId, s, sf.event(), sf.data());
                                    return s;
                                }).subscribeOn(Schedulers.boundedElastic()))
                                .doOnComplete(() -> finishRun(runId, seq, AgentRunStatus.DONE))
                                .doOnError(err -> {
                                    log.warn("[AgentRunCoordinator] run failed runId={}: {}",
                                            runId, err.toString());
                                    finishRun(runId, seq, AgentRunStatus.FAILED);
                                })
                                .onErrorResume(err -> Mono.empty())
                                .subscribe();
                    } catch (RuntimeException err) {
                        log.warn("[AgentRunCoordinator] launch failed runId={}: {}",
                                runId, err.toString());
                        finishRun(runId, seq, AgentRunStatus.FAILED);
                    } finally {
                        WorkspaceContextHolder.clear();
                        RequestContextHolder.resetRequestAttributes();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private void finishRun(String runId, AtomicLong seq, AgentRunStatus status) {
        try {
            long s = seq.incrementAndGet();
            eventStore.append(runId, s, "run_finished",
                    writeJson(Map.of("runId", runId, "status", status.name())));
            eventStore.complete(runId, status);
        } catch (RuntimeException e) {
            log.warn("[AgentRunCoordinator] finishRun error runId={}: {}", runId, e.toString());
            eventStore.complete(runId, status);
        }
    }

    /**
     * 先回放缓冲，再轮询新事件直到终态且追上 lastSeq。
     */
    private Flux<AgentRunStreamEvent> subscribe(String runId, long afterSeq) {
        return Flux.<AgentRunStreamEvent>create(sink -> {
                    long cursor = afterSeq;
                    try {
                        while (!sink.isCancelled()) {
                            List<AgentRunStreamEvent> batch = eventStore.listAfter(runId, cursor);
                            for (AgentRunStreamEvent ev : batch) {
                                if (sink.isCancelled()) {
                                    return;
                                }
                                sink.next(ev);
                                cursor = ev.getSeq();
                            }
                            AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(runId).orElse(null);
                            if (meta == null) {
                                sink.error(new BusinessException(
                                        BizCode.RUN_NOT_FOUND.getCode(),
                                        BizCode.RUN_NOT_FOUND.getMessage()));
                                return;
                            }
                            if (meta.status().terminal() && cursor >= meta.lastSeq()) {
                                sink.complete();
                                return;
                            }
                            try {
                                Thread.sleep(pollInterval.toMillis());
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                sink.complete();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        if (!sink.isCancelled()) {
                            sink.error(e);
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String ensureSession(String agentNum, String sessionNum, String operatorId,
                                 String origin, Map<String, Object> context) {
        if (StrUtil.isNotBlank(sessionNum)) {
            return sessionNum;
        }
        SessionDTO created = sessionCommandService.createSession(
                agentNum, "", "", operatorId, origin, context);
        return created.getNum();
    }

    private ResolvedCursor resolveCursor(String sessionNum, String runIdHint, String lastEventId) {
        String runId = StrUtil.trimToNull(runIdHint);
        long afterSeq = 0L;
        if (StrUtil.isNotBlank(lastEventId)) {
            String[] parts = lastEventId.split(":", 2);
            if (parts.length != 2 || StrUtil.isBlank(parts[0])) {
                throw new BusinessException(BizCode.CURSOR_INVALID.getCode(),
                        BizCode.CURSOR_INVALID.getMessage());
            }
            if (runId != null && !runId.equals(parts[0])) {
                throw new BusinessException(BizCode.CURSOR_INVALID.getCode(),
                        "Last-Event-ID 与 runId 不一致");
            }
            runId = parts[0];
            try {
                afterSeq = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                throw new BusinessException(BizCode.CURSOR_INVALID.getCode(),
                        BizCode.CURSOR_INVALID.getMessage());
            }
        }
        if (runId == null) {
            runId = eventStore.findRunningRunId(sessionNum)
                    .orElseThrow(() -> new BusinessException(
                            BizCode.RUN_NOT_FOUND.getCode(),
                            "续传需要 runId/lastEventId，或会话存在进行中的 run"));
        }
        if (!eventStore.getMeta(runId).isPresent()) {
            throw new BusinessException(BizCode.CURSOR_EXPIRED.getCode(),
                    BizCode.CURSOR_EXPIRED.getMessage());
        }
        return new ResolvedCursor(runId, afterSeq);
    }

    private SerializedFrame serializeFrame(AgentInvokeFrame frame) {
        try {
            if (frame.isSandboxStatus()) {
                SandboxStatusEvent status = frame.getSandboxStatus();
                return new SerializedFrame(
                        SandboxStatusEvent.SSE_EVENT,
                        objectMapper.writeValueAsString(status));
            }
            if (frame.isUserInputRequired()) {
                return new SerializedFrame(
                        frame.getSseEventName(),
                        objectMapper.writeValueAsString(frame.getUserInputRequired()));
            }
            if (frame.isUserInputStatus()) {
                return new SerializedFrame(
                        frame.getSseEventName(),
                        objectMapper.writeValueAsString(frame.getUserInputStatusPayload()));
            }
            JsonNode root = objectMapper.valueToTree(frame.getAgentEvent());
            return new SerializedFrame(null, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.error("[AgentRunCoordinator] serialize frame failed", e);
            return new SerializedFrame(null, "{}");
        }
    }

    private AgentRunStreamEvent finishedEvent(String runId, long seq, AgentRunStatus status) {
        return AgentRunStreamEvent.builder()
                .id(runId + ":" + seq)
                .seq(seq)
                .event("run_finished")
                .data(writeJson(Map.of("runId", runId, "status", status.name())))
                .build();
    }

    private String writeJson(Map<String, ?> map) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            map.forEach((k, v) -> {
                if (v == null) {
                    node.putNull(k);
                } else {
                    node.put(k, String.valueOf(v));
                }
            });
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static boolean isBlankContent(NormalizedInvokeContent content) {
        if (content == null) {
            return true;
        }
        return StrUtil.isBlank(content.getText()) && !content.hasAttachments();
    }

    private record ResolvedCursor(String runId, long afterSeq) {
    }

    private record SerializedFrame(String event, String data) {
    }
}
