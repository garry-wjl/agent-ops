package ink.garry.rd.agent.ws.application.agentrunner.run;

import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;

/**
 * 把 Agent 帧写进 run 事件库，并保证这次 run 一定会进入终态。
 * <p>
 * 限时计时放在独立的 timer 上，不跟模型调用抢 {@code boundedElastic}。
 * 到点、失败、取消都只收尾一次。
 */
final class AgentRunFrameWriter {

    private AgentRunFrameWriter() {
    }

    static Disposable subscribe(
            Flux<AgentInvokeFrame> frames,
            Duration timeout,
            Scheduler timer,
            Scheduler appendScheduler,
            Consumer<AgentInvokeFrame> appendFrame,
            Consumer<AgentRunStatus> finish,
            Consumer<Throwable> onError) {
        AtomicBoolean finished = new AtomicBoolean(false);
        Consumer<AgentRunStatus> once = status -> {
            if (finished.compareAndSet(false, true)) {
                finish.accept(status);
            }
        };
        return frames.concatMap(frame -> Mono.fromCallable(() -> {
                    appendFrame.accept(frame);
                    return frame;
                }).subscribeOn(appendScheduler))
                .timeout(timeout, timer)
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE) {
                        once.accept(AgentRunStatus.DONE);
                    } else if (signal == SignalType.ON_ERROR || signal == SignalType.CANCEL) {
                        once.accept(AgentRunStatus.FAILED);
                    }
                })
                .doOnError(onError)
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
