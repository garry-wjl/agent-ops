package ink.garry.rd.agent.ws.application.tool.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaDefaultValueBuilderTest {

    @Test
    @SuppressWarnings("unchecked")
    void build_nestedObjectArrayMap() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "default", "alice"),
                        "tags", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string", "default", "t")),
                        "attrs", Map.of(
                                "type", "object",
                                "x-field-kind", "map",
                                "additionalProperties", Map.of("type", "boolean", "default", true)
                        )
                )
        );

        Object built = JsonSchemaDefaultValueBuilder.build(schema);
        assertInstanceOf(Map.class, built);
        Map<String, Object> body = (Map<String, Object>) built;
        assertEquals("alice", body.get("name"));
        assertEquals(List.of("t"), body.get("tags"));
        assertTrue(body.get("attrs") instanceof Map);
        assertEquals(true, ((Map<?, ?>) body.get("attrs")).get("key"));
    }
}
