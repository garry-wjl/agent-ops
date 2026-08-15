package ink.garry.rd.agent.ws.application.a2ui;

import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2UI action → Agent 输入 / 上下文合并。
 */
class OpenA2uiInvokeServiceActionMappingTest {

    @Test
    void buildActionInput_shouldIncludeActionAndClientDataModel() {
        OpenA2uiActionParam param = new OpenA2uiActionParam();
        OpenA2uiActionParam.A2uiActionPayload action = new OpenA2uiActionParam.A2uiActionPayload();
        action.setName("submit_form");
        action.setSurfaceId("main");
        action.setSourceComponentId("btn_ok");
        action.setTimestamp("2026-08-15T01:00:00Z");
        action.setContext(Map.of("email", "a@b.com"));
        param.setAction(action);
        param.setClientDataModel(Map.of("main", Map.of("email", "a@b.com")));

        String input = OpenA2uiInvokeService.buildActionInput(param);
        assertTrue(input.contains("submit_form"));
        assertTrue(input.contains("btn_ok"));
        assertTrue(input.contains("a@b.com"));
        assertTrue(input.contains("clientDataModel"));
    }

    @Test
    void mergeActionContext_shouldPreferExplicitContextKeys() {
        OpenA2uiActionParam param = new OpenA2uiActionParam();
        OpenA2uiActionParam.A2uiActionPayload action = new OpenA2uiActionParam.A2uiActionPayload();
        action.setName("go");
        action.setSurfaceId("main");
        action.setSourceComponentId("b1");
        action.setTimestamp("t");
        action.setContext(Map.of("k", "from-action"));
        param.setAction(action);
        param.setClientDataModel(Map.of("main", Map.of("x", 1)));
        param.setContext(Map.of("a2uiActionName", "overridden", "extra", "yes"));

        Map<String, Object> merged = OpenA2uiInvokeService.mergeActionContext(param);
        assertEquals("overridden", merged.get("a2uiActionName"));
        assertEquals("yes", merged.get("extra"));
        assertEquals(Map.of("main", Map.of("x", 1)), merged.get("a2uiClientDataModel"));
    }
}
