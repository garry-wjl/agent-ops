package ink.garry.rd.agent.ws.domain.agent.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolRef#bindingKey()} / {@link ToolRef#isConcreteItem()} 契约测试（双模式绑定）。
 */
class ToolRefTest {

    @Test
    void wholeGroup_shouldUseToolNumAsKey() {
        ToolRef ref = ToolRef.builder().toolNum("TOOL-1").build();
        assertFalse(ref.isConcreteItem());
        assertEquals("TOOL-1", ref.bindingKey());
    }

    @Test
    void fcEndpoint_shouldNormalizeMethodAndIncludePath() {
        ToolRef ref = ToolRef.builder()
                .toolNum("FC-1")
                .itemKind(ToolRef.ITEM_FC_ENDPOINT)
                .method("get")
                .path("/users/{id}")
                .build();
        assertTrue(ref.isConcreteItem());
        assertEquals("FC-1|FC_ENDPOINT|GET|/users/{id}", ref.bindingKey());
    }

    @Test
    void mcpTool_shouldIncludeName() {
        ToolRef ref = ToolRef.builder()
                .toolNum("MCP-1")
                .itemKind(ToolRef.ITEM_MCP_TOOL)
                .mcpToolName("maps_weather")
                .build();
        assertEquals("MCP-1|MCP_TOOL|maps_weather", ref.bindingKey());
    }

    @Test
    void blankToolNum_shouldReturnEmptyKey() {
        assertEquals("", ToolRef.builder().toolNum("").build().bindingKey());
        assertEquals("", ToolRef.builder().toolNum(null).build().bindingKey());
    }
}
