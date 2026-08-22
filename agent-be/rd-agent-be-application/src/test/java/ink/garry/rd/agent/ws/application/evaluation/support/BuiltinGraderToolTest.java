package ink.garry.rd.agent.ws.application.evaluation.support;

import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.BuiltinGraderCode;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builtin TOOL_* 规则单测。
 */
class BuiltinGraderToolTest {

    private BuiltinGraderEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BuiltinGraderEngine();
    }

    @Test
    void toolCalled() {
        Map<String, Object> trace = Map.of("toolNames", List.of("search"));
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.TOOL_CALLED.name(),
                Map.of(), Map.of("trace", trace)).isPassed());
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.TOOL_CALLED.name(),
                Map.of(), Map.of("trace", Map.of("toolNames", List.of()))).isPassed());
    }

    @Test
    void toolNameContains() {
        Map<String, Object> trace = Map.of("toolNames", List.of("web_search", "calc"));
        Map<String, Object> cfg = Map.of("keyword", "search");
        assertTrue(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.TOOL_NAME_CONTAINS.name(),
                cfg, Map.of("trace", trace)).isPassed());
        assertFalse(engine.evaluateOne(GraderKind.BUILTIN.name(), BuiltinGraderCode.TOOL_NAME_CONTAINS.name(),
                Map.of("keyword", "missing"), Map.of("trace", trace)).isPassed());
    }

    @Test
    void extractToolNames_fromToolsField() {
        Map<String, Object> trace = Map.of("tools", List.of(Map.of("name", "fetch")));
        List<String> names = engine.extractToolNames(trace);
        assertTrue(names.contains("fetch"));
    }
}
