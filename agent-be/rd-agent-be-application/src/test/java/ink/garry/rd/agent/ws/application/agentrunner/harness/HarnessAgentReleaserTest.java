package ink.garry.rd.agent.ws.application.agentrunner.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求结束时关闭本次 HarnessAgent。不支持子 Agent，因此不遍历 agent_spawn。
 */
class HarnessAgentReleaserTest {

    @Test
    @DisplayName("关闭可关闭的 Agent")
    void release_closesAgent() {
        TrackingAgent agent = new TrackingAgent("main");
        HarnessAgentReleaser.release(agent);
        assertTrue(agent.closed);
    }

    @Test
    @DisplayName("close 失败时不向外抛")
    void release_swallowsCloseFailure() {
        TrackingAgent agent = new TrackingAgent("main");
        agent.closeError = new IllegalStateException("close failed");
        HarnessAgentReleaser.release(agent);
        assertTrue(agent.closeAttempted);
    }

    @Test
    @DisplayName("不能关闭的 Agent 被忽略")
    void release_ignoresNonCloseable() {
        PlainAgent plain = new PlainAgent();
        HarnessAgentReleaser.release(plain);
        HarnessAgentReleaser.release(null);
        assertFalse(plain.touched);
    }

    /**
     * 可关闭的 Agent 替身。不 mock HarnessAgent：它的字节码版本当前 JaCoCo 插桩不了。
     */
    private static class TrackingAgent implements Agent, AutoCloseable {
        private final String name;
        private boolean closed;
        private boolean closeAttempted;
        private RuntimeException closeError;

        TrackingAgent(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            closeAttempted = true;
            if (closeError != null) {
                throw closeError;
            }
            closed = true;
        }

        @Override
        public String getAgentId() {
            return name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void interrupt() {
        }

        @Override
        public void interrupt(Msg msg) {
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, Class<?> structuredModel) {
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, JsonNode schema) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(Msg msg) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(List<Msg> msgs) {
            return Mono.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema) {
            return Flux.empty();
        }
    }

    /** 只实现 Agent，没有 close()。 */
    private static final class PlainAgent implements Agent {
        private boolean touched;

        @Override
        public String getAgentId() {
            touched = true;
            return "plain";
        }

        @Override
        public String getName() {
            touched = true;
            return "plain";
        }

        @Override
        public void interrupt() {
            touched = true;
        }

        @Override
        public void interrupt(Msg msg) {
            touched = true;
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs) {
            touched = true;
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, Class<?> structuredModel) {
            touched = true;
            return Mono.empty();
        }

        @Override
        public Mono<Msg> call(List<Msg> msgs, JsonNode schema) {
            touched = true;
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(Msg msg) {
            touched = true;
            return Mono.empty();
        }

        @Override
        public Mono<Void> observe(List<Msg> msgs) {
            touched = true;
            return Mono.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options) {
            touched = true;
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, Class<?> structuredModel) {
            touched = true;
            return Flux.empty();
        }

        @Override
        public Flux<Event> stream(List<Msg> msgs, StreamOptions options, JsonNode schema) {
            touched = true;
            return Flux.empty();
        }
    }
}
