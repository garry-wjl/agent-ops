package ink.garry.rd.agent.ws.application.evaluation.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatasetSchemaFlattener 单测：列展开、反展平、导出扁平化。
 */
class DatasetSchemaFlattenerTest {

    @Test
    void columnHeaders_expandsObjectProperties() {
        String schema = """
                [
                  {"name":"input","type":"string"},
                  {"name":"context","type":"object","properties":{
                    "orderId":{"type":"string"},
                    "profile":{"type":"object","properties":{"city":{"type":"string"}}}
                  }}
                ]
                """;
        List<String> cols = DatasetSchemaFlattener.columnHeaders(schema, 3);
        assertEquals(List.of("input", "context.orderId", "context.profile.city"), cols);
    }

    @Test
    void columnHeaders_expandsArraySlots() {
        String schema = """
                [
                  {"name":"tags","type":"array","items":{"type":"string"}},
                  {"name":"items","type":"array","items":{"type":"object","properties":{
                    "sku":{"type":"string"},"qty":{"type":"string"}
                  }}}
                ]
                """;
        List<String> cols = DatasetSchemaFlattener.columnHeaders(schema, 2);
        assertTrue(cols.contains("tags[0]"));
        assertTrue(cols.contains("tags[1]"));
        assertTrue(cols.contains("items[0].sku"));
        assertTrue(cols.contains("items[0].qty"));
        assertTrue(cols.contains("items[1].sku"));
    }

    @Test
    void unflatten_nestedObjectAndArray() {
        Map<String, String> flat = new LinkedHashMap<>();
        flat.put("input", "你好");
        flat.put("context.orderId", "ORD-1");
        flat.put("context.profile.city", "上海");
        flat.put("tags[0]", "a");
        flat.put("tags[1]", "b");
        flat.put("items[0].sku", "S1");

        Map<String, Object> nested = DatasetSchemaFlattener.unflatten(flat);
        assertEquals("你好", nested.get("input"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) nested.get("context");
        assertEquals("ORD-1", ctx.get("orderId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) ctx.get("profile");
        assertEquals("上海", profile.get("city"));
        assertInstanceOf(List.class, nested.get("tags"));
        assertEquals(List.of("a", "b"), nested.get("tags"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) nested.get("items");
        assertEquals("S1", items.get(0).get("sku"));
    }

    @Test
    void flattenToColumns_roundTrip() {
        String schema = """
                [{"name":"input","type":"string"},
                 {"name":"context","type":"object","properties":{"orderId":{"type":"string"}}}]
                """;
        List<String> cols = DatasetSchemaFlattener.columnHeaders(schema, 3);
        Map<String, String> flat = DatasetSchemaFlattener.flattenToColumns(
                "{\"input\":\"hi\",\"context\":{\"orderId\":\"X\"}}", cols);
        assertEquals("hi", flat.get("input"));
        assertEquals("X", flat.get("context.orderId"));
    }

    @Test
    void objectWithoutProperties_staysLeaf() {
        String schema = "[{\"name\":\"context\",\"type\":\"object\"}]";
        assertEquals(List.of("context"), DatasetSchemaFlattener.columnHeaders(schema, 3));
    }
}
