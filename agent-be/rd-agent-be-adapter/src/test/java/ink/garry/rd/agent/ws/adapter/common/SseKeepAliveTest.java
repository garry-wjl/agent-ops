package ink.garry.rd.agent.ws.adapter.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseKeepAliveTest {

    @Test
    void withHeartbeat_heartbeatIsSseComment_notDataEvent() {
        Flux<ServerSentEvent<String>> neverEnding = Flux.never();

        List<ServerSentEvent<String>> heartbeats = SseKeepAlive.withHeartbeat(neverEnding, Duration.ofMillis(20))
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(heartbeats).isNotNull().hasSize(3);
        for (ServerSentEvent<String> hb : heartbeats) {
            assertThat(hb.comment()).isEqualTo("heartbeat");
            assertThat(hb.data()).isNull();
            assertThat(hb.event()).isNull();
        }
    }

    @Test
    void withHeartbeat_fastBusinessComplete_emitsOnlyBusinessEvents() {
        Flux<ServerSentEvent<String>> business = Flux.just(event("a"), event("b"));

        List<ServerSentEvent<String>> all = SseKeepAlive.withHeartbeat(business, Duration.ofMillis(200))
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(all).isNotNull().hasSize(2);
        assertThat(all).extracting(ServerSentEvent::data).containsExactly("a", "b");
        assertThat(all).allMatch(e -> e.comment() == null);
    }

    @Test
    void withHeartbeat_emitsHeartbeatsWhileBusinessIdle() {
        Flux<ServerSentEvent<String>> business = Flux.<ServerSentEvent<String>>just(event("hello"))
                .concatWith(Flux.<ServerSentEvent<String>>never().take(Duration.ofMillis(70)))
                .concatWith(Flux.just(event("bye")));

        List<ServerSentEvent<String>> all = SseKeepAlive.withHeartbeat(business, Duration.ofMillis(20))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(all).isNotNull();
        assertThat(all.get(0).data()).isEqualTo("hello");
        assertThat(all.get(all.size() - 1).data()).isEqualTo("bye");
        long heartbeats = all.stream().filter(e -> "heartbeat".equals(e.comment())).count();
        assertThat(heartbeats).isGreaterThanOrEqualTo(2);
    }

    @Test
    void withHeartbeat_stopsWhenBusinessErrors() {
        Flux<ServerSentEvent<String>> failing = Flux.concat(
                Flux.just(event("ok")),
                Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(SseKeepAlive.withHeartbeat(failing, Duration.ofSeconds(10)))
                .expectNextMatches(e -> "ok".equals(e.data()))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void withHeartbeat_nullSource_returnsEmpty() {
        StepVerifier.create(SseKeepAlive.withHeartbeat(null))
                .verifyComplete();
    }

    @Test
    void defaultInterval_isTenSeconds() {
        assertThat(SseKeepAlive.DEFAULT_INTERVAL).isEqualTo(Duration.ofSeconds(10));
    }

    private static ServerSentEvent<String> event(String data) {
        return ServerSentEvent.<String>builder().event("message").data(data).build();
    }
}
