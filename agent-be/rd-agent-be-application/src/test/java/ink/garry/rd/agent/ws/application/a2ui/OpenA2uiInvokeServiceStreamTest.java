package ink.garry.rd.agent.ws.application.a2ui;

import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiInvokeParam;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenA2uiInvokeService 编排自动化：mock Agent 执行流，断言 A2UI envelope 序列。
 */
@ExtendWith(MockitoExtension.class)
class OpenA2uiInvokeServiceStreamTest {

    @Mock
    private OpenAgentInvokeService openAgentInvokeService;

    @InjectMocks
    private OpenA2uiInvokeService openA2uiInvokeService;

    @Test
    void invoke_shouldEmitBootstrapThenAssistantTextUpdates() {
        when(openAgentInvokeService.invoke(eq("AGT1"), eq("hello"), isNull(), isNull(), isNull()))
                .thenReturn(Flux.just(
                        new Event(EventType.REASONING,
                                Msg.builder().content(TextBlock.builder().text("嗨").build()).build(),
                                false),
                        new Event(EventType.REASONING,
                                Msg.builder().content(TextBlock.builder().text("嗨呀").build()).build(),
                                true),
                        new Event(EventType.AGENT_RESULT,
                                Msg.builder().content(TextBlock.builder().text("嗨呀").build()).build(),
                                true)));

        OpenA2uiInvokeParam param = new OpenA2uiInvokeParam();
        param.setAgentNum("AGT1");
        param.setInput("hello");
        param.setSurfaceId("main");
        param.setSendDataModel(true);

        List<Map<String, Object>> envelopes = openA2uiInvokeService.invoke(param).collectList().block();
        assertTrue(envelopes != null && envelopes.size() >= 4);
        assertTrue(envelopes.get(0).containsKey("createSurface"));
        assertTrue(envelopes.get(1).containsKey("updateComponents"));
        assertTrue(envelopes.get(2).containsKey("updateDataModel"));
        assertEquals(A2uiProtocol.VERSION, envelopes.get(0).get("version"));

        // 最后一个带文本的 updateDataModel 应为「嗨呀」
        Object lastText = null;
        for (Map<String, Object> env : envelopes) {
            if (env.containsKey("updateDataModel")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) env.get("updateDataModel");
                lastText = body.get("value");
            }
        }
        assertEquals("嗨呀", lastText);
    }

    @Test
    @SuppressWarnings("unchecked")
    void action_shouldForwardStructuredInputAndContextToAgent() {
        when(openAgentInvokeService.invoke(eq("AGT1"), anyString(), eq("SES1"), eq("op-1"), any()))
                .thenReturn(Flux.empty());

        OpenA2uiActionParam param = new OpenA2uiActionParam();
        param.setAgentNum("AGT1");
        param.setSessionNum("SES1");
        param.setOperatorId("op-1");
        OpenA2uiActionParam.A2uiActionPayload action = new OpenA2uiActionParam.A2uiActionPayload();
        action.setName("submit_form");
        action.setSurfaceId("main");
        action.setSourceComponentId("btn");
        action.setTimestamp("2026-08-15T02:00:00Z");
        action.setContext(Map.of("email", "u@example.com"));
        param.setAction(action);
        param.setClientDataModel(Map.of("main", Map.of("email", "u@example.com")));

        List<Map<String, Object>> envelopes = openA2uiInvokeService.action(param).collectList().block();
        // 仅 bootstrap（无 Agent 事件）
        assertEquals(3, envelopes.size());
        assertTrue(envelopes.get(0).containsKey("createSurface"));

        ArgumentCaptor<String> inputCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> ctxCap = ArgumentCaptor.forClass(Map.class);
        verify(openAgentInvokeService).invoke(
                eq("AGT1"), inputCap.capture(), eq("SES1"), eq("op-1"), ctxCap.capture());
        assertTrue(inputCap.getValue().contains("submit_form"));
        assertTrue(inputCap.getValue().contains("u@example.com"));
        assertEquals("submit_form", ctxCap.getValue().get("a2uiActionName"));
        assertEquals(Map.of("main", Map.of("email", "u@example.com")),
                ctxCap.getValue().get("a2uiClientDataModel"));
    }
}
