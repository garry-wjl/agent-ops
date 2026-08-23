package ink.garry.rd.agent.ws.application.tool.factory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI requestBody → 端点 schema + LLM parameters.body。
 */
class ToolRunnerFactoryRequestBodyTest {

    @Test
    @SuppressWarnings("unchecked")
    void parseOpenApiEndpoints_shouldCaptureRequestBodySchema() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod("parseOpenApiEndpoints", JSONObject.class);
        method.setAccessible(true);

        JSONObject root = JSON.parseObject("""
                {
                  "openapi": "3.0.1",
                  "paths": {
                    "/users": {
                      "post": {
                        "summary": "create",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": { "$ref": "#/components/schemas/User" }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "User": {
                        "type": "object",
                        "properties": { "name": { "type": "string" } },
                        "required": ["name"]
                      }
                    }
                  }
                }
                """);

        List<ApiEndpointDTO> endpoints = (List<ApiEndpointDTO>) method.invoke(factory, root);
        assertEquals(1, endpoints.size());
        ApiEndpointDTO ep = endpoints.get(0);
        assertEquals("POST", ep.getMethod());
        assertNotNull(ep.getRequestBodySchema());
        assertEquals("object", ep.getRequestBodySchema().get("type"));
        assertEquals(Boolean.TRUE, ep.getRequestBodyRequired());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildParameters_shouldNestBodySchema() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod("buildParameters", ApiEndpointDTO.class);
        method.setAccessible(true);

        ApiEndpointDTO endpoint = ApiEndpointDTO.builder()
                .method("POST")
                .path("/users")
                .requestBodyRequired(true)
                .requestBodySchema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
                        "required", List.of("name")))
                .build();

        Map<String, Object> parameters = (Map<String, Object>) method.invoke(factory, endpoint);
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertTrue(properties.containsKey(ToolRunnerFactory.BODY_PARAM_NAME));
        assertEquals(endpoint.getRequestBodySchema(), properties.get(ToolRunnerFactory.BODY_PARAM_NAME));
        List<String> required = (List<String>) parameters.get("required");
        assertTrue(required.contains(ToolRunnerFactory.BODY_PARAM_NAME));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildParameters_shouldRespectQueryRequiredFlag() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod("buildParameters", ApiEndpointDTO.class);
        method.setAccessible(true);

        ApiEndpointDTO endpoint = ApiEndpointDTO.builder()
                .method("GET")
                .path("/items")
                .queryParams(List.of(
                        ApiParamDTO.builder().name("q").type("STRING").required(true).build(),
                        ApiParamDTO.builder().name("page").type("INTEGER").required(false)
                                .defaultValue("1").build()))
                .build();

        Map<String, Object> parameters = (Map<String, Object>) method.invoke(factory, endpoint);
        List<String> required = (List<String>) parameters.get("required");
        assertTrue(required.contains("q"));
        assertFalse(required.contains("page"));
    }

    @Test
    void description_shouldAppendResponseBodySchema() throws Exception {
        ToolRunnerFactory factory = new ToolRunnerFactory();
        Method method = ToolRunnerFactory.class.getDeclaredMethod(
                "description",
                ink.garry.rd.agent.ws.client.tool.dto.ToolDTO.class,
                ApiEndpointDTO.class);
        method.setAccessible(true);

        var tool = ink.garry.rd.agent.ws.client.tool.dto.ToolDTO.builder()
                .description("tool-desc")
                .build();
        ApiEndpointDTO endpoint = ApiEndpointDTO.builder()
                .method("GET")
                .path("/x")
                .description("ep-desc")
                .responseBodySchema(Map.of(
                        "type", "object",
                        "properties", Map.of("code", Map.of("type", "integer"))))
                .build();

        String desc = (String) method.invoke(factory, tool, endpoint);
        assertTrue(desc.startsWith("ep-desc"));
        assertTrue(desc.contains("返回结构(JSON Schema)"));
        assertTrue(desc.contains("code"));
    }
}
