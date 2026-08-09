package ink.garry.rd.agent.ws.application.tool.factory;

import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                "Content-Length", "12",
                // 调试台 SSE 入站头：绝不能透传到 MCP streamable-http
                "Accept", "text/event-stream",
                "Content-Type", "application/json",
                "Cookie", "SESSION=abc");

        Map<String, String> headers = (Map<String, String>) method.invoke(factory, config, tool, inbound);

        assertEquals("overridden", headers.get("X-Inbound"));
        assertEquals("from-config", headers.get("X-Config"));
        assertEquals("from-proxy", headers.get("X-Proxy"));
        assertEquals("Bearer ak-xxx", headers.get("Authorization"));
        assertEquals("SESSION=abc", headers.get("Cookie"));
        assertFalse(headers.containsKey("Host"));
        assertFalse(headers.keySet().stream().anyMatch(k -> "host".equalsIgnoreCase(k)));
        assertTrue(headers.keySet().stream().noneMatch(k -> "content-length".equalsIgnoreCase(k)));
        assertTrue(headers.keySet().stream().noneMatch(k -> "accept".equalsIgnoreCase(k)));
        assertTrue(headers.keySet().stream().noneMatch(k -> "content-type".equalsIgnoreCase(k)));
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

    @Test
    @SuppressWarnings("unchecked")
    void setNonForwardableHeaders_blank_shouldKeepDefaults() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        factory.setNonForwardableHeaders("  ");
        assertEquals(ToolRunnerFactory.DEFAULT_NON_FORWARDABLE_HEADERS, factoryNonForwardable(factory));

        Method method = ToolRunnerFactory.class.getDeclaredMethod(
                "buildMcpHeaders", JSONObject.class, ToolDTO.class, Map.class);
        method.setAccessible(true);
        ToolDTO tool = new ToolDTO();
        tool.setProxyEnabled(false);
        Map<String, String> headers = (Map<String, String>) method.invoke(
                factory, new JSONObject(), tool, Map.of("Accept", "text/event-stream", "X-Keep", "1"));
        assertEquals("1", headers.get("X-Keep"));
        assertTrue(headers.keySet().stream().noneMatch(k -> "accept".equalsIgnoreCase(k)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void setNonForwardableHeaders_nonBlank_shouldReplaceDefaultsEntirely() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        // 整表覆盖：只拦 X-Custom；Accept 不再在黑名单中，应透传
        factory.setNonForwardableHeaders("X-Custom, Host");
        assertEquals(Set.of("x-custom", "host"), factoryNonForwardable(factory));

        Method method = ToolRunnerFactory.class.getDeclaredMethod(
                "buildMcpHeaders", JSONObject.class, ToolDTO.class, Map.class);
        method.setAccessible(true);
        ToolDTO tool = new ToolDTO();
        tool.setProxyEnabled(false);
        Map<String, String> headers = (Map<String, String>) method.invoke(
                factory,
                new JSONObject(),
                tool,
                Map.of(
                        "Accept", "text/event-stream",
                        "X-Custom", "blocked",
                        "Host", "agent-ops.example.com",
                        "X-Keep", "ok"));

        assertEquals("text/event-stream", headers.get("Accept"));
        assertEquals("ok", headers.get("X-Keep"));
        assertTrue(headers.keySet().stream().noneMatch(k -> "x-custom".equalsIgnoreCase(k)));
        assertTrue(headers.keySet().stream().noneMatch(k -> "host".equalsIgnoreCase(k)));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> factoryNonForwardable(ToolRunnerFactory factory) throws Exception {
        var field = ToolRunnerFactory.class.getDeclaredField("nonForwardableHeaders");
        field.setAccessible(true);
        return (Set<String>) field.get(factory);
    }
}
