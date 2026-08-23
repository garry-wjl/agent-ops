package ink.garry.rd.agent.ws.application.tool.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiSchemaResolverTest {

    @Test
    void extractRequestBodySchema_shouldResolveLocalRef() {
        JSONObject root = JSON.parseObject("""
                {
                  "paths": {
                    "/users": {
                      "post": {
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": { "$ref": "#/components/schemas/CreateUser" }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "CreateUser": {
                        "type": "object",
                        "required": ["name"],
                        "properties": {
                          "name": { "type": "string" },
                          "email": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """);
        JSONObject operation = root.getJSONObject("paths")
                .getJSONObject("/users")
                .getJSONObject("post");

        Map<String, Object> schema = OpenApiSchemaResolver.extractRequestBodySchema(operation, root);
        Boolean required = OpenApiSchemaResolver.extractRequestBodyRequired(operation);

        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.get("properties") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("email"));
        assertEquals(List.of("name"), schema.get("required"));
        assertEquals(Boolean.TRUE, required);
    }

    @Test
    void extractRequestBodySchema_inlineSchema_noRequestBody_returnsNull() {
        JSONObject root = JSON.parseObject("""
                {
                  "paths": {
                    "/users": {
                      "get": { "summary": "list" }
                    }
                  }
                }
                """);
        JSONObject operation = root.getJSONObject("paths")
                .getJSONObject("/users")
                .getJSONObject("get");

        assertNull(OpenApiSchemaResolver.extractRequestBodySchema(operation, root));
        assertNull(OpenApiSchemaResolver.extractRequestBodyRequired(operation));
    }

    @Test
    void extractRequestBodySchema_shouldPreferApplicationJson() {
        JSONObject root = JSON.parseObject("""
                {
                  "paths": {
                    "/echo": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "text/plain": {
                              "schema": { "type": "string" }
                            },
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": { "ok": { "type": "boolean" } }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """);
        JSONObject operation = root.getJSONObject("paths")
                .getJSONObject("/echo")
                .getJSONObject("post");

        Map<String, Object> schema = OpenApiSchemaResolver.extractRequestBodySchema(operation, root);
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
    }
}
