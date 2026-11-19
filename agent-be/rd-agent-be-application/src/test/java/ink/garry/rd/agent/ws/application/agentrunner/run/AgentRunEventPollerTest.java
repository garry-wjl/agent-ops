package ink.garry.rd.agent.ws.application.agentrunner.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 事件跟随不能在等待下一拍时占住调用线程。
 */
class AgentRunEventPollerTest {

    @Test
    void waitBetweenPollsLeavesTheSchedulerFree() throws Exception {
        AgentRunEventStore store = mock(AgentRunEventStore.class);
        CountDownLatch firstPoll = new CountDownLatch(1);
        AtomicInteger polls = new AtomicInteger();
        when(store.listAfter(anyString(), anyLong())).thenAnswer(inv -> {
            polls.incrementAndGet();
            firstPoll.countDown();
            return List.of();
        });
        when(store.getMeta(anyString())).thenReturn(Optional.of(running()));

        Scheduler scheduler = Schedulers.newSingle("agent-run-poll-test");
        try {
            var subscription = AgentRunEventPoller.follow(
                            store, "RUN1", 0L, Duration.ofSeconds(30), scheduler)
                    .subscribe();
            assertThat(firstPoll.await(2, TimeUnit.SECONDS)).isTrue();

            CountDownLatch interloper = new CountDownLatch(1);
            scheduler.schedule(interloper::countDown);
            assertThat(interloper.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(polls).hasValue(1);
            subscription.dispose();
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void emitsBufferedEventsAndCompletesWhenRunIsTerminal() throws Exception {
        AgentRunEventStore store = mock(AgentRunEventStore.class);
        AtomicInteger polls = new AtomicInteger();
        when(store.listAfter(anyString(), anyLong())).thenAnswer(inv -> {
            int n = polls.incrementAndGet();
            if (n == 1) {
                return List.of();
            }
            return List.of(AgentRunStreamEvent.builder()
                    .id("RUN1:1")
                    .seq(1)
                    .event("run_finished")
                    .data("{}")
                    .build());
        });
        when(store.getMeta(anyString())).thenAnswer(inv -> Optional.of(
                polls.get() < 2
                        ? running()
                        : new AgentRunEventStore.AgentRunMeta(
                                "RUN1", "SES1", "AGT1", AgentRunStatus.DONE, 1)));

        Scheduler scheduler = Schedulers.newSingle("agent-run-poll-done");
        try {
            AtomicReference<AgentRunStreamEvent> seen = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            AgentRunEventPoller.follow(store, "RUN1", 0L, Duration.ofMillis(30), scheduler)
                    .subscribe(seen::set, err -> done.countDown(), done::countDown);
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get()).isNotNull();
            assertThat(seen.get().getSeq()).isEqualTo(1);
            assertThat(seen.get().getEvent()).isEqualTo("run_finished");
        } finally {
            scheduler.dispose();
        }
    }

    private static AgentRunEventStore.AgentRunMeta running() {
        return new AgentRunEventStore.AgentRunMeta(
                "RUN1", "SES1", "AGT1", AgentRunStatus.RUNNING, 0);
    }
}
