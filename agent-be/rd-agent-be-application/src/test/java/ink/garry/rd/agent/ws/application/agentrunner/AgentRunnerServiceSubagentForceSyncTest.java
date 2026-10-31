package ink.garry.rd.agent.ws.application.agentrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentRunnerServiceSubagentForceSyncTest {

    @Test
    @DisplayName("开启强制同步时写入 CTX_FORCE_SYNC 与超时秒数（默认 600，覆盖开放/调试台）")
    void apply_whenEnabled_putsForceSyncContext() {
        RuntimeContext.Builder builder = RuntimeContext.builder().userId("u1").sessionId("s1");
        AgentRunnerService.applySubagentForceSync(builder, true, 600);
        RuntimeContext ctx = builder.build();
        assertEquals(Boolean.TRUE, ctx.get(AgentSpawnTool.CTX_FORCE_SYNC));
        assertEquals(Integer.valueOf(600), ctx.get(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS));
    }

    @Test
    @DisplayName("关闭时不写入强制同步上下文")
    void apply_whenDisabled_skips() {
        RuntimeContext.Builder builder = RuntimeContext.builder().userId("u1").sessionId("s1");
        AgentRunnerService.applySubagentForceSync(builder, false, 120);
        RuntimeContext ctx = builder.build();
        assertNull(ctx.get(AgentSpawnTool.CTX_FORCE_SYNC));
        assertNull(ctx.get(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS));
    }

    @Test
    @DisplayName("超时秒数钳制到 [1, 600]")
    void apply_clampsTimeout() {
        RuntimeContext.Builder low = RuntimeContext.builder().userId("u1").sessionId("s1");
        AgentRunnerService.applySubagentForceSync(low, true, 0);
        assertEquals(Integer.valueOf(1), low.build().get(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS));

        RuntimeContext.Builder high = RuntimeContext.builder().userId("u1").sessionId("s1");
        AgentRunnerService.applySubagentForceSync(high, true, 9999);
        assertEquals(Integer.valueOf(600), high.build().get(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS));
    }

    @Test
    @DisplayName("AgentScope 2.0.4 暴露 CTX_FORCE_SYNC 常量")
    void agentscopeExposesForceSyncConstants() {
        assertTrue(AgentSpawnTool.CTX_FORCE_SYNC.contains("force_sync"));
        assertTrue(AgentSpawnTool.CTX_FORCE_SYNC_TIMEOUT_SECONDS.contains("timeout"));
    }
}
