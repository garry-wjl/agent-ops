package ink.garry.rd.agent.ws.application.tool.factory;

import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP REMOTE 出站请求头装配：入站透传 → mcpConfig.headers → proxyHeaders。
 */
class ToolRunnerFactoryMcpHeadersTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildMcpHeaders_shouldForwardInboundThenOverrideByConfigAndProxy() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod(
                "buildMcpHeaders", JSONObject.class, ToolDTO.class, Map.class);
        method.setAccessible(true);

        JSONObject config = new JSONObject();
        config.put("headers", Map.of("X-Config", "from-config", "X-Inbound", "overridden"));

        ToolDTO tool = new ToolDTO();
        tool.setProxyEnabled(true);
        ProxyHeaderDTO proxy = new ProxyHeaderDTO();
        proxy.setName("X-Proxy");
        proxy.setValue("from-proxy");
        tool.setProxyHeaders(List.of(proxy));

        Map<String, String> inbound = Map.of(
                "X-Inbound", "from-api",
                "Authorization", "Bearer ak-xxx",
                "Host", "agent-ops.example.com",
                "Content-Length", "12");

        Map<String, String> headers = (Map<String, String>) method.invoke(factory, config, tool, inbound);

        assertEquals("overridden", headers.get("X-Inbound"));
        assertEquals("from-config", headers.get("X-Config"));
        assertEquals("from-proxy", headers.get("X-Proxy"));
        assertEquals("Bearer ak-xxx", headers.get("Authorization"));
        assertFalse(headers.containsKey("Host"));
        assertFalse(headers.keySet().stream().anyMatch(k -> "host".equalsIgnoreCase(k)));
        assertTrue(headers.keySet().stream().noneMatch(k -> "content-length".equalsIgnoreCase(k)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMcpHeaders_emptyInbound_shouldStillApplyConfig() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod(
                "buildMcpHeaders", JSONObject.class, ToolDTO.class, Map.class);
        method.setAccessible(true);

        JSONObject config = new JSONObject();
        config.put("headers", Map.of("Authorization", "Bearer mcp-key"));
        ToolDTO tool = new ToolDTO();
        tool.setProxyEnabled(false);

        Map<String, String> headers =
                (Map<String, String>) method.invoke(factory, config, tool, Map.of());

        assertEquals(Map.of("Authorization", "Bearer mcp-key"), headers);
    }
}
