package ink.garry.rd.agent.ws.application.evaluation.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CodeGraderRunner SpEL 单测。
 */
class CodeGraderRunnerTest {

    private CodeGraderRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CodeGraderRunner();
    }

    @Test
    void booleanScript() {
        Map<String, Object> vars = Map.of("response", "hello world", "reference", "world");
        var r = runner.evaluateOne("CODE", null,
                Map.of("script", "#response != null and #response.contains(#reference)"),
                vars);
        assertTrue(r.isPassed());
    }

    @Test
    void numberScript() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("response", "x");
        var r = runner.evaluateOne("CODE", null,
                Map.of("script", "0.7", "passThreshold", new BigDecimal("0.5")),
                vars);
        assertTrue(r.isPassed());
        assertTrue(r.getScore().compareTo(new BigDecimal("0.7")) == 0);
    }

    @Test
    void invalidReturnType() {
        var r = runner.evaluateOne("CODE", null, Map.of("script", "'text'"), Map.of());
        assertFalse(r.isPassed());
    }

    @Test
    void evaluateBinding_customVariableNames() {
        GraderBindingSnapshot binding = new GraderBindingSnapshot();
        binding.setGraderNum("G_CODE");
        binding.setKind("CODE");
        binding.setMapping(Map.of(
                "answer", "$actual_output",
                "gold", "$row.expected_answer"));
        binding.setConfigSnapshot(Map.of(
                "script", "#answer != null and #answer.contains(#gold)"));

        var ok = runner.evaluateBinding(
                binding,
                Map.of("expected_answer", "退款"),
                "已完成退款处理",
                null);
        assertTrue(ok.isPassed());

        var fail = runner.evaluateBinding(
                binding,
                Map.of("expected_answer", "退款"),
                "仅查询进度",
                null);
        assertFalse(fail.isPassed());
    }
}
