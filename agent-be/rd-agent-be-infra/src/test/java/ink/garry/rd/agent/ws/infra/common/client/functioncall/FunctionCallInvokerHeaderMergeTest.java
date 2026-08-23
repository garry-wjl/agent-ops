package ink.garry.rd.agent.ws.infra.common.client.functioncall;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FunctionCall 出站头：调试台 SSE 的 Accept/Content-Type 不得覆盖业务 API 内容协商。
 */
class FunctionCallInvokerHeaderMergeTest {

    @Test
    void mergeHeaders_shouldDropDebugConsoleAcceptAndContentType() {
        Map<String, String> inbound = Map.of(
                "Accept", "text/event-stream",
                "Content-Type", "application/json",
                "Authorization", "Bearer user-token",
                "X-Workspace-Num", "WS-1",
                "Host", "localhost:8081",
                "Cookie", "SESSION=abc");
        Map<String, String> endpoint = Map.of(
                "X-Api-Key", "tool-key",
                "Authorization", "Bearer tool-token");

        Map<String, String> merged = FunctionCallInvoker.mergeHeaders(inbound, endpoint);

        assertFalse(merged.keySet().stream().anyMatch(k -> "accept".equalsIgnoreCase(k)));
        assertFalse(merged.keySet().stream().anyMatch(k -> "content-type".equalsIgnoreCase(k)));
        assertFalse(merged.keySet().stream().anyMatch(k -> "host".equalsIgnoreCase(k)));
        assertEquals("WS-1", merged.get("X-Workspace-Num"));
        assertEquals("SESSION=abc", merged.get("Cookie"));
        assertEquals("tool-key", merged.get("X-Api-Key"));
        // 端点配置覆盖同名入站头
        assertEquals("Bearer tool-token", merged.get("Authorization"));
    }

    @Test
    void isForwardableInboundHeader_shouldBlockNegotiationHeaders() {
        assertFalse(FunctionCallInvoker.isForwardableInboundHeader("Accept"));
        assertFalse(FunctionCallInvoker.isForwardableInboundHeader("content-type"));
        assertFalse(FunctionCallInvoker.isForwardableInboundHeader("Host"));
        assertTrue(FunctionCallInvoker.isForwardableInboundHeader("Authorization"));
        assertTrue(FunctionCallInvoker.isForwardableInboundHeader("X-Custom"));
    }
}
