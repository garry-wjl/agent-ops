package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseGenResultParserTest {

    private static final String SCHEMA = """
            [{"name":"input","type":"string"},{"name":"reference","type":"string"}]
            """;

    @Test
    void parseJsonArray() {
        String raw = """
                [{"input":"a","reference":"b"},{"input":"c","reference":"d"}]
                """;
        var out = CaseGenResultParser.parse(raw, SCHEMA, 50);
        assertEquals(2, out.parsedCount());
        assertEquals(2, out.validDataJsonList().size());
        assertEquals(0, out.skippedCount());
    }

    @Test
    void parseMarkdownFenceAndSkipInvalid() {
        String raw = """
                以下是结果：
                ```json
                [{"input":"ok"},{"foo":1},{"input":"ok2","reference":"r"}]
                ```
                """;
        var out = CaseGenResultParser.parse(raw, SCHEMA, 50);
        assertEquals(3, out.parsedCount());
        assertEquals(2, out.validDataJsonList().size());
        assertEquals(1, out.skippedCount());
    }

    @Test
    void parseJsonlAndRespectCap() {
        String raw = """
                {"input":"1"}
                {"input":"2"}
                {"input":"3"}
                """;
        var out = CaseGenResultParser.parse(raw, SCHEMA, 2);
        assertEquals(3, out.parsedCount());
        assertEquals(2, out.validDataJsonList().size());
        assertEquals(1, out.skippedCount());
    }

    @Test
    void emptyOutput_returnsZeros() {
        var out = CaseGenResultParser.parse("   ", SCHEMA, 50);
        assertEquals(0, out.parsedCount());
        assertEquals(0, out.validDataJsonList().size());
    }

    @Test
    void singleObjectAccepted() {
        var out = CaseGenResultParser.parse("{\"input\":\"x\",\"reference\":\"y\"}", SCHEMA, 50);
        assertEquals(1, out.validDataJsonList().size());
    }

    @Test
    void noSchemaFields_acceptAnyObject() {
        var out = CaseGenResultParser.parse("[{\"foo\":1}]", "[]", 50);
        assertEquals(1, out.validDataJsonList().size());
    }

    @Test
    void extractFromSurroundingNoise() {
        String raw = "说明如下\n[{\"input\":\"a\"}]\n完";
        var out = CaseGenResultParser.parse(raw, SCHEMA, 50);
        assertEquals(1, out.validDataJsonList().size());
    }

    @Test
    void matchesSchemaRequiresTopField() {
        assertTrue(CaseGenResultParser.matchesSchema(
                com.alibaba.fastjson2.JSON.parseObject("{\"input\":\"x\"}"),
                CaseGenResultParser.topLevelFieldNames(SCHEMA)));
        assertFalse(CaseGenResultParser.matchesSchema(
                com.alibaba.fastjson2.JSON.parseObject("{\"zzz\":1}"),
                CaseGenResultParser.topLevelFieldNames(SCHEMA)));
    }
}
