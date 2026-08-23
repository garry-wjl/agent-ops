package ink.garry.rd.agent.ws.application.tool;

import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.MountableToolItemDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolQueryService} FC 端点展平为可挂载项。
 */
class ToolQueryServiceMountableItemsTest {

    @Test
    @SuppressWarnings("unchecked")
    void expandFunctionCallItems_shouldBuildBindingKeys() throws Exception {
        ToolQueryService service = new ToolQueryService();
        Method method = ToolQueryService.class.getDeclaredMethod("expandFunctionCallItems", ToolDTO.class);
        method.setAccessible(true);

        ToolDTO tool = ToolDTO.builder()
                .num("FC-9")
                .name("Demo FC")
                .type(ToolType.FUNCTION_CALL.name())
                .endpoints(List.of(
                        ApiEndpointDTO.builder().method("get").path("/a").description("A").build(),
                        ApiEndpointDTO.builder().method("POST").path("/b").description("B").build()))
                .build();

        List<MountableToolItemDTO> items = (List<MountableToolItemDTO>) method.invoke(service, tool);
        assertEquals(2, items.size());
        assertEquals("FC-9|FC_ENDPOINT|GET|/a", items.get(0).getBindingKey());
        assertEquals("FC-9|FC_ENDPOINT|POST|/b", items.get(1).getBindingKey());
        assertEquals("Demo FC", items.get(0).getToolName());
        assertTrue(items.get(0).getName().contains("/a"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expandFunctionCallItems_emptyEndpoints_returnsEmpty() throws Exception {
        ToolQueryService service = new ToolQueryService();
        Method method = ToolQueryService.class.getDeclaredMethod("expandFunctionCallItems", ToolDTO.class);
        method.setAccessible(true);

        ToolDTO tool = ToolDTO.builder()
                .num("FC-empty")
                .name("Empty")
                .type(ToolType.FUNCTION_CALL.name())
                .build();
        List<MountableToolItemDTO> items = (List<MountableToolItemDTO>) method.invoke(service, tool);
        assertTrue(items.isEmpty());
    }
}
