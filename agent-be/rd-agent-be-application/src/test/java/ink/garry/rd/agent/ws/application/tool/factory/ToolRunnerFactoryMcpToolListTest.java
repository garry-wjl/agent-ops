package ink.garry.rd.agent.ws.application.tool.factory;

import ink.garry.rd.agent.ws.client.tool.dto.McpRemoteToolInfoDTO;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 试连 listTools → 摘要 DTO 映射。
 */
class ToolRunnerFactoryMcpToolListTest {

    @Test
    void toRemoteToolInfos_shouldMapNameTitleDescription() {
        McpSchema.JsonSchema input = new McpSchema.JsonSchema(
                "object",
                Map.of("address", Map.of("type", "string", "description", "地址")),
                List.of("address"),
                null,
                null,
                null);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("maps_geo")
                .title("地理编码")
                .description("将地址转为坐标")
                .inputSchema(input)
                .outputSchema(Map.of("type", "object", "properties", Map.of("lng", Map.of("type", "number"))))
                .build();
        List<McpRemoteToolInfoDTO> infos = ToolRunnerFactory.toRemoteToolInfos(List.of(tool));
        assertEquals(1, infos.size());
        assertEquals("maps_geo", infos.get(0).getName());
        assertEquals("地理编码", infos.get(0).getTitle());
        assertEquals("将地址转为坐标", infos.get(0).getDescription());
        assertNotNull(infos.get(0).getInputSchema());
        assertEquals("object", infos.get(0).getInputSchema().get("type"));
        assertNotNull(infos.get(0).getOutputSchema());
        assertEquals("object", infos.get(0).getOutputSchema().get("type"));
    }

    @Test
    void toRemoteToolInfos_nullOrEmpty_shouldReturnEmpty() {
        assertTrue(ToolRunnerFactory.toRemoteToolInfos(null).isEmpty());
        assertTrue(ToolRunnerFactory.toRemoteToolInfos(List.of()).isEmpty());
    }
}
