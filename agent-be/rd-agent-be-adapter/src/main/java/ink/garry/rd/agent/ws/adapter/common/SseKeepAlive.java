package ink.garry.rd.agent.ws.adapter.common;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * SSE 保活：在业务事件流上合并周期性心跳，避免空闲时被网关 / 负载均衡掐断。
 * <p>
 * 心跳使用 SSE comment（{@code :heartbeat}），前端解析器忽略 comment，不影响业务事件。
 */
public final class SseKeepAlive {

    /** 默认心跳间隔 */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(10);

    private SseKeepAlive() {
    }

    /**
     * 在业务 SSE 流上叠加心跳；业务流结束或出错时心跳一并停止。
     *
     * @param events 业务事件流
     * @return 合并后的流
     */
    public static Flux<ServerSentEvent<String>> withHeartbeat(Flux<ServerSentEvent<String>> events) {
        return withHeartbeat(events, DEFAULT_INTERVAL);
    }

    /**
     * @param events   业务事件流
     * @param interval 心跳间隔；须为正
     * @return 合并后的流
     */
    public static Flux<ServerSentEvent<String>> withHeartbeat(Flux<ServerSentEvent<String>> events,
                                                             Duration interval) {
        if (events == null) {
            return Flux.empty();
        }
        Duration tick = interval == null || interval.isZero() || interval.isNegative()
                ? DEFAULT_INTERVAL
                : interval;
        return events.publish(shared -> Flux.merge(
                shared,
                Flux.interval(tick)
                        .map(i -> ServerSentEvent.<String>builder().comment("heartbeat").build())
                        .takeUntilOther(shared.ignoreElements())));
    }
}
