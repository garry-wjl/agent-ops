package ink.garry.rd.agent.ws.application.evaluation.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmGraderSupport 单元测试。
 */
class LlmGraderSupportTest {

    @Test
    void renderTemplate_replacesVariables() {
        String tpl = "输出: {{response}}, 参考: {{reference}}";
        String out = LlmGraderSupport.renderTemplate(tpl, Map.of("response", "hi", "reference", "bye"));
        assertEquals("输出: hi, 参考: bye", out);
    }

    @Test
    void renderTemplate_customNamesAndMissingBecomeEmpty() {
        String out = LlmGraderSupport.renderTemplate(
                "a={{answer}}; g={{gold}}; m={{missing}}",
                Map.of("answer", "实际", "gold", "期望"));
        assertEquals("a=实际; g=期望; m=", out);
    }

    @Test
    void appendOutputFormatInstruction_usesScoreRangeAndIsIdempotent() {
        String once = LlmGraderSupport.appendOutputFormatInstruction(
                "请评估语义是否正确",
                BigDecimal.ZERO,
                new BigDecimal("100"));
        assertTrue(once.contains("0 到 100"));
        assertTrue(once.contains("{\"score\":"));
        assertTrue(once.contains(LlmGraderSupport.OUTPUT_FORMAT_MARKER));

        String twice = LlmGraderSupport.appendOutputFormatInstruction(
                once, BigDecimal.ZERO, new BigDecimal("100"));
        assertEquals(once, twice);

        String custom = LlmGraderSupport.buildOutputFormatInstruction(
                new BigDecimal("10"), new BigDecimal("50"));
        assertTrue(custom.contains("10 到 50"));
    }

    @Test
    void parseScoreResponse_jsonAndLoose() {
        LlmGraderSupport.ParsedScore json = LlmGraderSupport.parseScoreResponse(
                "{\"score\":0.8,\"reason\":\"good\"}", BigDecimal.ZERO, BigDecimal.ONE);
        assertEquals(new BigDecimal("0.8"), json.score());
        assertEquals("good", json.reason());

        LlmGraderSupport.ParsedScore loose = LlmGraderSupport.parseScoreResponse(
                "score: 0.6", BigDecimal.ZERO, BigDecimal.ONE);
        assertEquals(new BigDecimal("0.6"), loose.score());
    }

    @Test
    void parseScoreResponse_clampAndFail() {
        LlmGraderSupport.ParsedScore high = LlmGraderSupport.parseScoreResponse(
                "{\"score\":2}", BigDecimal.ZERO, BigDecimal.ONE);
        assertEquals(BigDecimal.ONE, high.score());

        LlmGraderSupport.ParsedScore fail = LlmGraderSupport.parseScoreResponse("no score here", BigDecimal.ZERO, BigDecimal.ONE);
        assertNull(fail.score());
        assertNotNull(fail.reason());
    }
}
