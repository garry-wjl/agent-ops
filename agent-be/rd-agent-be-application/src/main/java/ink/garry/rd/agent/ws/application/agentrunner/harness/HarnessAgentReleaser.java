package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请求结束时释放 HarnessAgent，避免子 Agent 常驻堆内存。
 * <p>
 * AgentScope 在 {@code ReActAgent} 构造时把实例登记进 JVM 全局
 * {@code GracefulShutdownManager}，只有 {@code close()} 才会注销。
 * {@code agent_spawn} 每建一个子 Agent 都会再登记一份，并且 {@link AgentSpawnTool}
 * 用内部 map 一直握着它们。调用链不 close 的话，这些对象会活到进程重启。
 * <p>
 * 先关已 spawn 的子 Agent（叶子节点没有 {@code agent_spawn}），再关主 Agent。
 * SDK 的 {@code HarnessAgent.close()} 不会级联关闭子 Agent，所以由这里补上。
 */
@Slf4j
public final class HarnessAgentReleaser {

    /** Harness 注册到 Toolkit 上的 spawn 工具名。 */
    static final String SPAWN_TOOL_NAME = "agent_spawn";

    private HarnessAgentReleaser() {
    }

    /**
     * 在 boundedElastic 上释放，避免 {@code close()} 里的阻塞等待卡住 SSE 线程。
     * 调度失败时改为当前线程直接释放，避免再漏掉。
     *
     * @param agent 本次调用装配出的 Agent；非 Harness 时直接返回
     */
    public static void releaseAsync(Agent agent) {
        if (!(agent instanceof AutoCloseable)) {
            return;
        }
        try {
            Schedulers.boundedElastic().schedule(() -> release(agent));
        } catch (RuntimeException e) {
            log.warn("异步释放 HarnessAgent 调度失败，改为当前线程释放: {}", e.toString());
            release(agent);
        }
    }

    /**
     * 同步释放主 Agent 及其已 spawn 的子 Agent。
     *
     * @param agent 本次调用装配出的 Agent；非 Harness 时直接返回
     */
    public static void release(Agent agent) {
        release(agent, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void release(Agent agent, Set<Agent> seen) {
        if (agent == null || !seen.add(agent) || !(agent instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeSpawnedSubagents(agent.getToolkit(), seen);
        } catch (Exception e) {
            log.warn("关闭已 spawn 子 Agent 失败 name={}: {}", safeName(agent), e.toString());
        } finally {
            closeQuietly(closeable, agent);
        }
    }

    private static void closeSpawnedSubagents(Toolkit toolkit, Set<Agent> seen) throws Exception {
        if (toolkit == null) {
            return;
        }
        AgentTool registered = toolkit.getTool(SPAWN_TOOL_NAME);
        Object raw = unwrapToolObject(registered);
        if (!(raw instanceof AgentSpawnTool spawnTool)) {
            return;
        }
        List<Agent> children = readSpawnedAgents(spawnTool);
        for (Agent child : children) {
            release(child, seen);
        }
        clearSpawnedAgents(spawnTool);
    }

    /**
     * {@code @Tool} 方法注册后，Toolkit 拿到的是包装对象，真正的 {@link AgentSpawnTool} 在 {@code toolObject} 字段里。
     */
    static Object unwrapToolObject(AgentTool tool) throws IllegalAccessException {
        if (tool == null) {
            return null;
        }
        Class<?> type = tool.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("toolObject");
                field.setAccessible(true);
                return field.get(tool);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Agent> readSpawnedAgents(AgentSpawnTool spawnTool) throws Exception {
        Map<Object, Object> agentsByKey = (Map<Object, Object>) agentsByKeyField().get(spawnTool);
        if (agentsByKey == null || agentsByKey.isEmpty()) {
            return List.of();
        }
        List<Agent> children = new ArrayList<>(agentsByKey.size());
        for (Object spawned : agentsByKey.values()) {
            if (spawned == null) {
                continue;
            }
            Method agentAccessor = spawned.getClass().getDeclaredMethod("agent");
            agentAccessor.setAccessible(true);
            Object child = agentAccessor.invoke(spawned);
            if (child instanceof Agent agent) {
                children.add(agent);
            }
        }
        return children;
    }

    @SuppressWarnings("unchecked")
    private static void clearSpawnedAgents(AgentSpawnTool spawnTool) throws Exception {
        Map<Object, Object> agentsByKey = (Map<Object, Object>) agentsByKeyField().get(spawnTool);
        if (agentsByKey != null) {
            agentsByKey.clear();
        }
        Field labelToKey = AgentSpawnTool.class.getDeclaredField("labelToKey");
        labelToKey.setAccessible(true);
        Map<Object, Object> labels = (Map<Object, Object>) labelToKey.get(spawnTool);
        if (labels != null) {
            labels.clear();
        }
    }

    private static Field agentsByKeyField() throws NoSuchFieldException {
        Field field = AgentSpawnTool.class.getDeclaredField("agentsByKey");
        field.setAccessible(true);
        return field;
    }

    private static void closeQuietly(AutoCloseable agent, Agent named) {
        try {
            agent.close();
        } catch (Exception e) {
            log.warn("关闭 HarnessAgent 失败 name={}: {}", safeName(named), e.toString());
        }
    }

    private static String safeName(Agent agent) {
        try {
            return agent == null ? "unknown" : agent.getName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
