package ink.garry.rd.agent.ws.application.agentrunner.run;

import static org.assertj.core.api.Assertions.assertThat;

import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 帧写完、超时或取消时，run 只能收尾一次。
 */
class AgentRunFrameWriterTest {

    @Test
    void completeMarksDoneOnce() throws Exception {
        AtomicInteger appends = new AtomicInteger();
        AtomicReference<AgentRunStatus> status = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        AgentRunFrameWriter.subscribe(
                Flux.just(AgentInvokeFrame.agent(null)),
                Duration.ofMinutes(16),
                Schedulers.parallel(),
                Schedulers.immediate(),
                frame -> appends.incrementAndGet(),
                next -> {
                    status.set(next);
                    done.countDown();
                },
                err -> {
                    throw new AssertionError(err);
                });
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(appends).hasValue(1);
        assertThat(status).hasValue(AgentRunStatus.DONE);
    }

    @Test
    void timeoutMarksFailedOnce() throws Exception {
        AtomicInteger finishes = new AtomicInteger();
        AtomicReference<AgentRunStatus> status = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        AgentRunFrameWriter.subscribe(
                Flux.never(),
                Duration.ofMillis(50),
                Schedulers.parallel(),
                Schedulers.immediate(),
                frame -> {
                    throw new AssertionError("no frame");
                },
                next -> {
                    finishes.incrementAndGet();
                    status.set(next);
                    done.countDown();
                },
                error::set);
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        assertThat(finishes).hasValue(1);
        assertThat(status).hasValue(AgentRunStatus.FAILED);
        assertThat(error.get()).isInstanceOf(TimeoutException.class);
    }

    @Test
    void cancelMarksFailed() {
        AtomicReference<AgentRunStatus> status = new AtomicReference<>();
        var subscription = AgentRunFrameWriter.subscribe(
                Flux.never(),
                Duration.ofHours(1),
                Schedulers.parallel(),
                Schedulers.immediate(),
                frame -> {
                },
                status::set,
                err -> {
                });
        subscription.dispose();
        assertThat(status).hasValue(AgentRunStatus.FAILED);
    }
}
