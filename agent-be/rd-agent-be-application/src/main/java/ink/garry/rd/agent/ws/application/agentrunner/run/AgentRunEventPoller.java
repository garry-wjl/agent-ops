package ink.garry.rd.agent.ws.application.agentrunner.run;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;

/**
 * 跟随 Redis 里的 run 事件。
 * <p>
 * 每一拍只读一次，然后用调度器预约下一拍。等待期间不占着 worker 线程。
 * 不能在 {@code boundedElastic} 上 {@code Thread.sleep}：那个池子每个 worker 只有一条线程，
 * 睡着的线程会把模型调用和结束信号堵在自己的队列里。
 */
final class AgentRunEventPoller {

    private AgentRunEventPoller() {
    }

    static Flux<AgentRunStreamEvent> follow(
            AgentRunEventStore eventStore,
            String runId,
            long afterSeq,
            Duration pollInterval,
            Scheduler scheduler) {
        Duration interval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                ? Duration.ofMillis(200)
                : pollInterval;
        return Flux.create(sink -> {
            AtomicLong cursor = new AtomicLong(afterSeq);
            AtomicBoolean stopped = new AtomicBoolean(false);
            AtomicReference<Disposable> pending = new AtomicReference<>();
            sink.onDispose(() -> {
                stopped.set(true);
                Disposable current = pending.getAndSet(null);
                if (current != null) {
                    current.dispose();
                }
            });
            Runnable tick = new Runnable() {
                @Override
                public void run() {
                    if (stopped.get() || sink.isCancelled()) {
                        return;
                    }
                    try {
                        long current = cursor.get();
                        List<AgentRunStreamEvent> batch = eventStore.listAfter(runId, current);
                        for (AgentRunStreamEvent event : batch) {
                            if (stopped.get() || sink.isCancelled()) {
                                return;
                            }
                            sink.next(event);
                            cursor.set(event.getSeq());
                        }
                        AgentRunEventStore.AgentRunMeta meta = eventStore.getMeta(runId).orElse(null);
                        if (meta == null) {
                            sink.error(new BusinessException(
                                    BizCode.RUN_NOT_FOUND.getCode(),
                                    BizCode.RUN_NOT_FOUND.getMessage()));
                            return;
                        }
                        if (meta.status().terminal() && cursor.get() >= meta.lastSeq()) {
                            sink.complete();
                            return;
                        }
                        // 还没追上时立刻读下一页，避免长任务的追赶被 200ms 间隔拖住。
                        Duration delay = !batch.isEmpty() && cursor.get() < meta.lastSeq()
                                ? Duration.ZERO
                                : interval;
                        scheduleNext(scheduler, this, delay, stopped, pending);
                    } catch (Exception ex) {
                        if (!stopped.get() && !sink.isCancelled()) {
                            sink.error(ex);
                        }
                    }
                }
            };
            scheduleNext(scheduler, tick, Duration.ZERO, stopped, pending);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private static void scheduleNext(
            Scheduler scheduler,
            Runnable tick,
            Duration delay,
            AtomicBoolean stopped,
            AtomicReference<Disposable> pending) {
        if (stopped.get()) {
            return;
        }
        Disposable next = scheduler.schedule(tick, delay.toMillis(), TimeUnit.MILLISECONDS);
        pending.set(next);
        if (stopped.get()) {
            next.dispose();
        }
    }
}
