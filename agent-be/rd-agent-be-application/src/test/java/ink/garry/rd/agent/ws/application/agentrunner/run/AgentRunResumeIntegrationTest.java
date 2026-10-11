package ink.garry.rd.agent.ws.application.agentrunner.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxStatusEvent;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 断线续传集成测试：真实 Redis Stream（docker redis-test / 127.0.0.1:6379 DB15）
 * + {@link AgentRunCoordinator}，mock 仅 {@link AgentRunnerService} 出帧节奏。
 * <p>
 * 覆盖：开跑写缓冲、首订户断开后 producer 不停、lastEventId 续传无漏无重、
 * SESSION_BUSY、结束后已收齐补 run_finished、SSE id 契约。
 */
class AgentRunResumeIntegrationTest {

    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    /** 隔离测试库，避免污染本地业务 DB0 */
    private static final int REDIS_DB = 15;

    private RedissonClient redisson;
    private RedisAgentRunEventStore eventStore;
    private AgentRunnerService agentRunnerService;
    private AgentRunCoordinator coordinator;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(redisReachable(),
                () -> "需要本机 Redis " + REDIS_HOST + ":" + REDIS_PORT
                        + "（docker redis-test）才能跑续传集成测");

        Config cfg = new Config();
        cfg.useSingleServer()
                .setAddress("redis://" + REDIS_HOST + ":" + REDIS_PORT)
                .setDatabase(REDIS_DB)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        redisson = Redisson.create(cfg);
        redisson.getKeys().flushdb();

        eventStore = new RedisAgentRunEventStore(redisson);
        ReflectionTestUtils.setField(eventStore, "ttlAfterFinish", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(eventStore, "ttlRunning", Duration.ofMinutes(10));
        ReflectionTestUtils.setField(eventStore, "maxEvents", 50_000L);

        agentRunnerService = mock(AgentRunnerService.class);
        coordinator = new AgentRunCoordinator(
                eventStore,
                agentRunnerService,
                mock(SessionCommandService.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(coordinator, "pollInterval", Duration.ofMillis(40));
    }

    @AfterEach
    void tearDown() {
        if (redisson != null) {
            try {
                redisson.getKeys().flushdb();
            } finally {
                redisson.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Redis Stream：create/append/listAfter/complete/running 索引闭环")
    void redisStore_appendListAfterComplete_roundTrip() {
        String runId = "RUNit-store-1";
        String session = "SES-it-store";
        eventStore.createRun(runId, session, "AGT-1");

        assertThat(eventStore.findRunningRunId(session)).contains(runId);
        assertThat(eventStore.getMeta(runId)).isPresent();
        assertThat(eventStore.getMeta(runId).orElseThrow().status()).isEqualTo(AgentRunStatus.RUNNING);

        eventStore.append(runId, 1, "run_started", "{\"runId\":\"" + runId + "\"}");
        eventStore.append(runId, 2, SandboxStatusEvent.SSE_EVENT, "{\"phase\":\"READY\"}");
        eventStore.append(runId, 3, null, "{\"type\":\"REASONING\"}");

        List<AgentRunStreamEvent> all = eventStore.listAfter(runId, 0);
        assertThat(all).extracting(AgentRunStreamEvent::getSeq).containsExactly(1L, 2L, 3L);
        assertThat(all).extracting(AgentRunStreamEvent::getId)
                .containsExactly(runId + ":1", runId + ":2", runId + ":3");
        assertThat(all.get(1).getEvent()).isEqualTo(SandboxStatusEvent.SSE_EVENT);

        assertThat(eventStore.listAfter(runId, 2))
                .extracting(AgentRunStreamEvent::getSeq)
                .containsExactly(3L);

        eventStore.complete(runId, AgentRunStatus.DONE);
        assertThat(eventStore.findRunningRunId(session)).isEmpty();
        assertThat(eventStore.getMeta(runId).orElseThrow().status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(eventStore.getMeta(runId).orElseThrow().lastSeq()).isEqualTo(3L);
        // 结束后缓冲仍可读（重连窗口）
        assertThat(eventStore.listAfter(runId, 0)).hasSize(3);
    }

    @Test
    @DisplayName("断线续传：首订户断开后 agent 不停；resume 不重跑、无漏无重、直至 DONE")
    void start_disconnect_resume_noRerun_noGap_noDup() throws Exception {
        String session = "SES-it-resume";
        AtomicInteger runnerCalls = new AtomicInteger();
        CountDownLatch midFrameLatch = new CountDownLatch(1);

        when(agentRunnerService.runAgentFrames(
                eq("AGT-1"),
                any(NormalizedInvokeContent.class),
                eq(session),
                eq("u1"),
                isNull(),
                eq("DEBUG_CONSOLE"),
                any()))
                .thenAnswer(inv -> {
                    runnerCalls.incrementAndGet();
                    return Flux.concat(
                                    Mono.just(statusFrame(session, "PREPARING")),
                                    Mono.delay(Duration.ofMillis(120))
                                            .then(Mono.fromRunnable(midFrameLatch::countDown))
                                            .then(Mono.just(statusFrame(session, "READY"))),
                                    Mono.delay(Duration.ofMillis(180))
                                            .then(Mono.just(statusFrame(session, "IDLE"))))
                            .subscribeOn(Schedulers.boundedElastic());
                });

        List<AgentRunStreamEvent> firstSeen = new CopyOnWriteArrayList<>();
        CountDownLatch gotTwo = new CountDownLatch(2);

        Disposable firstSub = coordinator.invokeOrResume(
                        false,
                        null,
                        null,
                        "AGT-1",
                        NormalizedInvokeContent.builder().text("hello").build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .subscribe(ev -> {
                    firstSeen.add(ev);
                    gotTwo.countDown();
                });

        assertThat(gotTwo.await(5, TimeUnit.SECONDS))
                .as("首订户应先收到至少 2 帧（run_started + PREPARING）")
                .isTrue();
        assertThat(firstSeen).hasSizeGreaterThanOrEqualTo(2);

        // 模拟 HTTP 断开：先取消订阅，再冻结游标（避免 dispose 后仍写入 firstSeen）
        firstSub.dispose();
        List<String> firstIds = firstSeen.stream().map(AgentRunStreamEvent::getId).toList();
        String lastEventId = firstIds.get(firstIds.size() - 1);
        String runId = lastEventId.split(":", 2)[0];
        assertThat(runId).startsWith("RUN");

        assertThat(midFrameLatch.await(5, TimeUnit.SECONDS))
                .as("断开后 producer 仍应继续出帧写入 Redis")
                .isTrue();
        // 再等 IDLE + run_finished 落盘
        awaitMetaTerminal(runId, Duration.ofSeconds(8));

        assertThat(runnerCalls.get()).isEqualTo(1);
        assertThat(eventStore.findRunningRunId(session)).isEmpty();
        AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(runId).orElseThrow();
        assertThat(meta.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(meta.lastSeq()).isGreaterThanOrEqualTo(5); // started + 3 status + finished

        List<AgentRunStreamEvent> buffered = eventStore.listAfter(runId, 0);
        List<Long> allSeqs = buffered.stream().map(AgentRunStreamEvent::getSeq).toList();
        assertThat(allSeqs).isSorted();
        assertThat(allSeqs).doesNotHaveDuplicates();
        assertThat(buffered.get(0).getEvent()).isEqualTo("run_started");
        assertThat(buffered.get(buffered.size() - 1).getEvent()).isEqualTo("run_finished");

        long afterSeq = Long.parseLong(lastEventId.split(":", 2)[1]);
        List<AgentRunStreamEvent> resumed = coordinator.invokeOrResume(
                        true,
                        lastEventId,
                        runId,
                        "AGT-1",
                        NormalizedInvokeContent.builder().build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .collectList()
                .block(Duration.ofSeconds(8));

        assertThat(resumed).isNotNull().isNotEmpty();
        assertThat(runnerCalls.get()).as("续传不得再次触发 runAgentFrames").isEqualTo(1);

        List<String> resumedIds = resumed.stream().map(AgentRunStreamEvent::getId).toList();
        assertThat(resumedIds).doesNotContainAnyElementsOf(firstIds);
        assertThat(resumed.stream().map(AgentRunStreamEvent::getSeq).toList())
                .allMatch(s -> s > afterSeq);

        // 首订户已收前缀 + 续传后缀 = 全量缓冲（无漏无重）
        List<String> expected = buffered.stream().map(AgentRunStreamEvent::getId).toList();
        assertThat(expected).startsWith(firstIds.toArray(String[]::new));
        assertThat(expected.subList(firstIds.size(), expected.size()))
                .containsExactlyElementsOf(resumedIds);

        // 同一会话新开跑应被放行（旧 run 已 DONE）
        when(agentRunnerService.runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Flux.empty());
        List<AgentRunStreamEvent> second = coordinator.invokeOrResume(
                        false,
                        null,
                        null,
                        "AGT-1",
                        NormalizedInvokeContent.builder().text("again").build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .take(1)
                .collectList()
                .block(Duration.ofSeconds(5));
        assertThat(second).isNotEmpty();
        assertThat(second.get(0).getEvent()).isEqualTo("run_started");
    }

    @Test
    @DisplayName("SESSION_BUSY：同会话已有 RUNNING 时禁止再开跑")
    void sessionBusy_secondStartRejected() throws Exception {
        when(agentRunnerService.runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Flux.never());

        String session = "SES-it-busy";
        Disposable sub = coordinator.invokeOrResume(
                        false,
                        null,
                        null,
                        "AGT-1",
                        NormalizedInvokeContent.builder().text("busy").build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .subscribe();

        try {
            awaitRunning(session, Duration.ofSeconds(3));
            assertThatThrownBy(() -> coordinator.invokeOrResume(
                            false,
                            null,
                            null,
                            "AGT-1",
                            NormalizedInvokeContent.builder().text("again").build(),
                            session,
                            "u1",
                            null,
                            "DEBUG_CONSOLE",
                            null)
                    .blockFirst(Duration.ofSeconds(2)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(BizCode.SESSION_BUSY.getCode()));
        } finally {
            sub.dispose();
            eventStore.findRunningRunId(session).ifPresent(rid ->
                    eventStore.complete(rid, AgentRunStatus.CANCELLED));
        }
    }

    @Test
    @DisplayName("结束后已收齐：resume 补一条合成 run_finished，且不重跑")
    void resume_afterFullyCaughtUp_emitsSyntheticFinished() {
        when(agentRunnerService.runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Flux.just(statusFrame("SES-done", "READY")));

        String session = "SES-it-done";
        List<AgentRunStreamEvent> all = coordinator.invokeOrResume(
                        false,
                        null,
                        null,
                        "AGT-1",
                        NormalizedInvokeContent.builder().text("done").build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .collectList()
                .block(Duration.ofSeconds(8));

        assertThat(all).isNotNull().isNotEmpty();
        AgentRunStreamEvent last = all.get(all.size() - 1);
        assertThat(last.getEvent()).isEqualTo("run_finished");
        verify(agentRunnerService, times(1)).runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any());

        List<AgentRunStreamEvent> again = coordinator.invokeOrResume(
                        true,
                        last.getId(),
                        last.getId().split(":")[0],
                        "AGT-1",
                        NormalizedInvokeContent.builder().build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(again).hasSize(1);
        assertThat(again.get(0).getEvent()).isEqualTo("run_finished");
        assertThat(again.get(0).getSeq()).isEqualTo(last.getSeq() + 1);
        verify(agentRunnerService, times(1)).runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("invokeOrResume：空白 content + lastEventId 自动进续传模式")
    void invokeOrResume_blankContentWithLastEventId_entersResume() {
        String runId = "RUNit-auto";
        String session = "SES-it-auto";
        eventStore.createRun(runId, session, "AGT-1");
        eventStore.append(runId, 1, "run_started", "{\"runId\":\"" + runId + "\"}");
        eventStore.append(runId, 2, SandboxStatusEvent.SSE_EVENT, "{\"phase\":\"READY\"}");
        eventStore.complete(runId, AgentRunStatus.DONE);

        List<AgentRunStreamEvent> events = coordinator.invokeOrResume(
                        false,
                        runId + ":1",
                        null,
                        "AGT-1",
                        NormalizedInvokeContent.builder().build(),
                        session,
                        "u1",
                        null,
                        "DEBUG_CONSOLE",
                        null)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).extracting(AgentRunStreamEvent::getId).containsExactly(runId + ":2");
        verify(agentRunnerService, times(0)).runAgentFrames(
                anyString(), any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("仅 session 续传：无 lastEventId 时从 running_run 解析")
    void resume_bySessionRunningIndex() {
        String runId = "RUNit-sess";
        String session = "SES-it-sess";
        eventStore.createRun(runId, session, "AGT-1");
        eventStore.append(runId, 1, "run_started", "{\"runId\":\"" + runId + "\"}");
        eventStore.append(runId, 2, SandboxStatusEvent.SSE_EVENT, "{\"phase\":\"PREPARING\"}");

        List<AgentRunStreamEvent> events = coordinator.resume(session, null, null)
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).extracting(AgentRunStreamEvent::getId)
                .containsExactly(runId + ":1", runId + ":2");

        eventStore.complete(runId, AgentRunStatus.CANCELLED);
    }

    private void awaitRunning(String session, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (eventStore.findRunningRunId(session).isPresent()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timeout waiting running run for " + session);
    }

    private void awaitMetaTerminal(String runId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(runId).orElse(null);
            if (meta != null && meta.status().terminal()) {
                return;
            }
            Thread.sleep(40);
        }
        AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(runId).orElse(null);
        String dump = eventStore.listAfter(runId, 0).stream()
                .map(e -> e.getId() + "/" + e.getEvent())
                .collect(Collectors.joining(", "));
        throw new AssertionError("timeout waiting terminal meta for " + runId
                + " meta=" + meta + " events=[" + dump + "]");
    }

    private static AgentInvokeFrame statusFrame(String sessionNum, String phase) {
        return AgentInvokeFrame.sandboxStatus(SandboxStatusEvent.builder()
                .sessionNum(sessionNum)
                .phase(phase)
                .ts(System.currentTimeMillis())
                .build());
    }

    private static boolean redisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 800);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
