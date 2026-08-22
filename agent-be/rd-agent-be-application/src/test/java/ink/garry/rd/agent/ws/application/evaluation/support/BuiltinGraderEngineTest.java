package ink.garry.rd.agent.ws.application.evaluation.support;

import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.BuiltinGraderCode;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BuiltinGraderEngine 单元测试：P0 四条内置规则。
 */
class BuiltinGraderEngineTest {

    private BuiltinGraderEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BuiltinGraderEngine();
    }

    @Test
    void nonEmpty_passAndFail() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("response", "hello");
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.NON_EMPTY.name(), Map.of(), vars).isPassed());
        vars.put("response", "  ");
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.NON_EMPTY.name(), Map.of(), vars).isPassed());
    }

    @Test
    void exactMatch_trimAndIgnoreCase() {
        Map<String, Object> vars = Map.of("response", " Hello ", "reference", "hello");
        Map<String, Object> cfg = Map.of("trim", true, "ignoreCase", true);
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.EXACT_MATCH.name(), cfg, vars).isPassed());
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.EXACT_MATCH.name(),
                Map.of("trim", true, "ignoreCase", false), vars).isPassed());
    }

    @Test
    void contains_allKeywords() {
        Map<String, Object> vars = Map.of("response", "订单已发货，请查收", "keywords", List.of("发货", "查收"));
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.CONTAINS.name(), Map.of(), vars).isPassed());
        vars = Map.of("response", "订单已发货", "keywords", List.of("发货", "查收"));
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.CONTAINS.name(), Map.of(), vars).isPassed());
    }

    @Test
    void jsonValid() {
        Map<String, Object> ok = Map.of("response", "{\"a\":1}");
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.JSON_VALID.name(), Map.of(), ok).isPassed());
        Map<String, Object> bad = Map.of("response", "not-json");
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.JSON_VALID.name(), Map.of(), bad).isPassed());
    }

    @Test
    void evaluateAll_overallPassRequiresAll() {
        GraderBindingSnapshot a = new GraderBindingSnapshot();
        a.setGraderNum("EGR1");
        a.setGraderVersion(1);
        a.setKind(GraderKind.BUILTIN.name());
        a.setBuiltinCode(BuiltinGraderCode.NON_EMPTY.name());
        a.setMapping(Map.of("response", "$actual_output"));

        GraderBindingSnapshot b = new GraderBindingSnapshot();
        b.setGraderNum("EGR2");
        b.setGraderVersion(1);
        b.setKind(GraderKind.BUILTIN.name());
        b.setBuiltinCode(BuiltinGraderCode.EXACT_MATCH.name());
        b.setMapping(Map.of("response", "$actual_output", "reference", "$row.reference"));

        List<ScoreResult> scores = engine.evaluateAll(
                List.of(a, b),
                Map.of("reference", "hi"),
                "hi",
                null);
        assertTrue(scores.stream().allMatch(ScoreResult::isPassed));

        scores = engine.evaluateAll(List.of(a, b), Map.of("reference", "bye"), "hi", null);
        assertFalse(scores.stream().allMatch(ScoreResult::isPassed));
    }

    @Test
    void evaluateBinding_customRowFieldAsReference() {
        GraderBindingSnapshot binding = new GraderBindingSnapshot();
        binding.setGraderNum("G1");
        binding.setKind(GraderKind.BUILTIN.name());
        binding.setBuiltinCode(BuiltinGraderCode.EXACT_MATCH.name());
        binding.setMapping(Map.of(
                "response", "$actual_output",
                "reference", "$row.expected_answer"));
        binding.setConfigSnapshot(Map.of("trim", true, "ignoreCase", false));

        assertTrue(engine.evaluateBinding(
                binding,
                Map.of("expected_answer", "hello", "reference", "wrong"),
                "hello",
                null).isPassed());

        assertFalse(engine.evaluateBinding(
                binding,
                Map.of("expected_answer", "hello", "reference", "hello"),
                "world",
                null).isPassed());
    }
}
