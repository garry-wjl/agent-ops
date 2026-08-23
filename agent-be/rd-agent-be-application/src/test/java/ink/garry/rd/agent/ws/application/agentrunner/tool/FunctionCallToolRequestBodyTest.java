package ink.garry.rd.agent.ws.application.agentrunner.tool;

import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpRequest;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpResponse;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionCallToolRequestBodyTest {

    @Test
    void callAsync_shouldSerializeBodyAndPassToInvoker() {
        AtomicReference<FunctionCallHttpRequest> captured = new AtomicReference<>();
        FunctionCallInvoker invoker = new FunctionCallInvoker() {
            @Override
            public FunctionCallHttpResponse invoke(FunctionCallHttpRequest request) {
                captured.set(request);
                return new FunctionCallHttpResponse(200, "{\"ok\":true}");
            }
        };

        ApiEndpointDTO endpoint = ApiEndpointDTO.builder()
                .method("POST")
                .path("/users")
                .requestBodyRequired(true)
                .requestBodySchema(Map.of("type", "object"))
                .build();

        FunctionCallTool tool = new FunctionCallTool(
                "create_user",
                "create",
                Map.of(),
                "https://api.example.com",
                endpoint,
                invoker,
                null);

        ToolCallParam param = ToolCallParam.builder()
                .input(Map.of("body", Map.of("name", "alice")))
                .build();

        tool.callAsync(param).block();

        FunctionCallHttpRequest req = captured.get();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertEquals("https://api.example.com/users", req.url());
        assertNotNull(req.body());
        assertTrue(req.body().contains("alice"));
        assertTrue(req.body().contains("name"));
    }
}
