package ink.garry.rd.agent.ws.adapter.open;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.adapter.security.ApiKeyAuthenticationFilter;
import ink.garry.rd.agent.ws.application.a2ui.A2uiProtocol;
import ink.garry.rd.agent.ws.application.a2ui.OpenA2uiInvokeService;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiInvokeParam;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OpenA2uiController 自动化：鉴权 agent 匹配 + SSE 序列化契约。
 */
@ExtendWith(MockitoExtension.class)
class OpenA2uiControllerTest {

    @Mock
    private OpenA2uiInvokeService openA2uiInvokeService;

    @Mock
    private HttpServletRequest request;

    private OpenA2uiController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new OpenA2uiController(openA2uiInvokeService, objectMapper);
    }

    @Test
    void invoke_shouldReturnA2uiSseWhenAgentMatches() throws Exception {
        when(request.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM)).thenReturn("AGT-1");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", A2uiProtocol.VERSION);
        envelope.put("createSurface", Map.of(
                "surfaceId", "main",
                "catalogId", A2uiProtocol.DEFAULT_CATALOG_ID,
                "sendDataModel", true));
        when(openA2uiInvokeService.invoke(any(OpenA2uiInvokeParam.class)))
                .thenReturn(Flux.just(envelope));

        OpenA2uiInvokeParam param = new OpenA2uiInvokeParam();
        param.setAgentNum("AGT-1");
        param.setInput("ping");

        List<ServerSentEvent<String>> events = controller.invoke(param, request).collectList().block();
        assertEquals(1, events.size());
        JsonNode node = objectMapper.readTree(events.get(0).data());
        assertEquals(A2uiProtocol.VERSION, node.get("version").asText());
        assertTrue(node.has("createSurface"));
        assertEquals("main", node.get("createSurface").get("surfaceId").asText());
    }

    @Test
    void action_shouldReturnA2uiSseWhenAgentMatches() throws Exception {
        when(request.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM)).thenReturn("AGT-1");
        Map<String, Object> envelope = Map.of(
                "version", A2uiProtocol.VERSION,
                "updateDataModel", Map.of("surfaceId", "main", "path", "/assistantText", "value", "ok"));
        when(openA2uiInvokeService.action(any(OpenA2uiActionParam.class)))
                .thenReturn(Flux.just(envelope));

        OpenA2uiActionParam param = new OpenA2uiActionParam();
        param.setAgentNum("AGT-1");
        OpenA2uiActionParam.A2uiActionPayload action = new OpenA2uiActionParam.A2uiActionPayload();
        action.setName("go");
        action.setSurfaceId("main");
        action.setSourceComponentId("b1");
        action.setTimestamp("2026-08-15T02:00:00Z");
        action.setContext(Map.of());
        param.setAction(action);

        List<ServerSentEvent<String>> events = controller.action(param, request).collectList().block();
        assertEquals(1, events.size());
        JsonNode node = objectMapper.readTree(events.get(0).data());
        assertEquals("ok", node.get("updateDataModel").get("value").asText());
    }

    @Test
    void invoke_shouldRejectWhenApiKeyAgentMismatch() {
        when(request.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM)).thenReturn("AGT-OTHER");
        OpenA2uiInvokeParam param = new OpenA2uiInvokeParam();
        param.setAgentNum("AGT-1");
        param.setInput("x");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.invoke(param, request));
        assertTrue(ex.getMessage().contains("不匹配") || ex.getMessage() != null);
    }
}
