package ink.garry.rd.agent.ws.application.agentrunner.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求结束时必须关掉已 spawn 的子 Agent，再关主 Agent。
 */
class HarnessAgentReleaserTest {

    @Test
    @DisplayName("释放主 Agent 时先关闭 agent_spawn 握着的子 Agent")
    void release_closesSpawnedChildrenThenParent() throws Exception {
        TrackingAgent child = new TrackingAgent("general-purpose-subagent", null);
        AgentSpawnTool spawnTool = spawnToolHolding(child);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(spawnTool);
        TrackingAgent parent = new TrackingAgent("main", toolkit);

        HarnessAgentReleaser.release(parent);

        assertTrue(child.closed);
        assertTrue(parent.closed);
        assertTrue(child.closedBefore(parent));
        assertTrue(readAgentsByKey(spawnTool).isEmpty());
    }

    @Test
    @DisplayName("子 Agent close 失败时仍关闭主 Agent")
    void release_closesParentWhenChildCloseFails() throws Exception {
        TrackingAgent child = new TrackingAgent("child", null);
        child.closeError = new IllegalStateException("child close failed");
        AgentSpawnTool spawnTool = spawnToolHolding(child);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(spawnTool);
        TrackingAgent parent = new TrackingAgent("main", toolkit);

        HarnessAgentReleaser.release(parent);

        assertTrue(parent.closed);
    }

    @Test
    @DisplayName("不可关闭的 Agent 不处理")
    void release_ignoresNonCloseable() {
        PlainAgent plain = new PlainAgent();
        HarnessAgentReleaser.release(plain);
        HarnessAgentReleaser.release(null);
        assertFalse(plain.touched);
    }

    private static AgentSpawnTool spawnToolHolding(Agent child) throws Exception {
        AgentSpawnTool tool = new AgentSpawnTool(new DefaultAgentManager(List.of(), null), null, 0);
        Class<?> spawnedClass = null;
        for (Class<?> nested : AgentSpawnTool.class.getDeclaredClasses()) {
            if ("SpawnedAgent".equals(nested.getSimpleName())) {
                spawnedClass = nested;
                break;
            }
        }
        Constructor<?> ctor = spawnedClass.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, Agent.class, int.class);
        ctor.setAccessible(true);
        Object spawned = ctor.newInstance(
                "agent:general-purpose:1", "general-purpose", "sub-1", null, child, 1);
        readAgentsByKey(tool).put("agent:general-purpose:1", spawned);
        return tool;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> readAgentsByKey(AgentSpawnTool tool) throws Exception {
        Field field = AgentSpawnTool.class.getDeclaredField("agentsByKey");
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(tool);
    }

    /**
     * 可关闭的 Agent 替身。不 mock HarnessAgent：它的字节码版本当前 JaCoCo 插桩不了。
     */
    private static class TrackingAgent implements Agent, AutoCloseable {
        private final String name;
        private final Toolkit toolkit;
        private boolean closed;
        private int closeOrder;
        private static int sequence;
        private RuntimeException closeError;

        TrackingAgent(String name, Toolkit toolkit) {
            this.name = name;
            this.toolkit = toolkit;
        }

        @Override
        public void close() {
            if (closeError != null) {
                throw closeError;
            }
            closed = true;
            closeOrder = ++sequence;
        }

        boolean closedBefore(TrackingAgent other) {
            return closed && other.closed && closeOrder < other.closeOrder;
        }

        @Override
        public Toolkit getToolkit() {
            return toolkit;
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
