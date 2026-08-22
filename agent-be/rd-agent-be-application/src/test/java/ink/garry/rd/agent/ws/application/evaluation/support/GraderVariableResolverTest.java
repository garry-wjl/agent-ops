package ink.garry.rd.agent.ws.application.evaluation.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变量 mapping 解析单测：默认约定 / 自定义变量 / 数据源路径。
 */
class GraderVariableResolverTest {

    @Test
    void resolve_emptyMapping_injectsDefaults() {
        Map<String, Object> row = Map.of("reference", "期望答案", "input", "问句");
        Map<String, Object> vars = GraderVariableResolver.resolve(
                null, row, "实际输出", Map.of("toolNames", java.util.List.of("search")));

        assertEquals("实际输出", vars.get("response"));
        assertEquals("实际输出", vars.get("actual_output"));
        assertEquals("期望答案", vars.get("reference"));
        assertEquals(row, vars.get("row"));
        assertTrue(vars.containsKey("trace"));
    }

    @Test
    void resolve_defaultProductMapping() {
        Map<String, String> mapping = Map.of(
                "response", "$actual_output",
                "reference", "$row.reference");
        Map<String, Object> vars = GraderVariableResolver.resolve(
                mapping,
                Map.of("reference", "金标"),
                "模型回复",
                null);

        assertEquals("模型回复", vars.get("response"));
        assertEquals("金标", vars.get("reference"));
        assertFalse(vars.containsKey("actual_output"), "非空 mapping 不自动注入未声明键");
        assertFalse(vars.containsKey("row"));
    }

    @Test
    void resolve_customVariableNames() {
        Map<String, String> mapping = Map.of(
                "answer", "$actual_output",
                "gold", "$row.expected_answer",
                "policy", "$row.policy",
                "runTrace", "$trace");
        Map<String, Object> row = Map.of(
                "expected_answer", "应退款",
                "policy", "P-01",
                "reference", "旧字段不应自动出现");
        Object trace = Map.of("toolNames", java.util.List.of("refund"));

        Map<String, Object> vars = GraderVariableResolver.resolve(mapping, row, "已退款成功", trace);

        assertEquals("已退款成功", vars.get("answer"));
        assertEquals("应退款", vars.get("gold"));
        assertEquals("P-01", vars.get("policy"));
        assertEquals(trace, vars.get("runTrace"));
        assertFalse(vars.containsKey("response"));
        assertFalse(vars.containsKey("reference"));
    }

    @Test
    void resolve_missingRowField_isNull() {
        Map<String, Object> vars = GraderVariableResolver.resolve(
                Map.of("gold", "$row.expected_answer"),
                Map.of("reference", "x"),
                "out",
                null);
        assertNull(vars.get("gold"));
    }

    @Test
    void resolvePath_literalAndNullExpr() {
        assertEquals("hello", GraderVariableResolver.resolvePath("hello", Map.of(), "a", null));
        assertNull(GraderVariableResolver.resolvePath(null, Map.of(), "a", null));
        assertEquals("a", GraderVariableResolver.resolvePath("  $actual_output  ", Map.of(), "a", null));
    }

    @Test
    void resolveForCode_baselineThenOverride() {
        Map<String, Object> vars = GraderVariableResolver.resolveForCode(
                Map.of("answer", "$actual_output", "reference", "$row.expected_answer"),
                Map.of("expected_answer", "金标", "reference", "旧reference"),
                "输出",
                "trace-obj");

        assertEquals("输出", vars.get("response"));
        assertEquals("输出", vars.get("actual_output"));
        assertEquals("输出", vars.get("answer"));
        assertEquals("金标", vars.get("reference"), "mapping 覆盖基线 reference");
        assertEquals("trace-obj", vars.get("trace"));
        assertTrue(vars.containsKey("row"));
    }

    @Test
    void renderTemplate_usesCustomResolvedVars() {
        Map<String, Object> vars = GraderVariableResolver.resolve(
                Map.of("answer", "$actual_output", "gold", "$row.expected_answer"),
                Map.of("expected_answer", "先发布评测集"),
                "需要先发布",
                null);
        String prompt = LlmGraderSupport.renderTemplate(
                "对比 answer={{answer}} 与 gold={{gold}}；缺失={{missing}}",
                vars);
        assertEquals("对比 answer=需要先发布 与 gold=先发布评测集；缺失=", prompt);
    }
}
