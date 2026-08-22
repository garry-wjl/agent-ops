package ink.garry.rd.agent.ws.application.evaluation.support;

import java.util.HashMap;
import java.util.Map;

/**
 * 评估器变量解析：将任务 mapping（变量名 → 数据源表达式）解析为运行时变量表。
 * <p>数据源：{@code $actual_output} / {@code $trace} / {@code $row.字段}；其它字面量原样返回。
 */
public final class GraderVariableResolver {

    private GraderVariableResolver() {
    }

    /**
     * LLM / Builtin 语义：mapping 为空时注入默认键；非空时仅注入 mapping 声明的键。
     */
    public static Map<String, Object> resolve(Map<String, String> mapping,
                                              Map<String, Object> row,
                                              String actualOutput,
                                              Object trace) {
        Map<String, Object> vars = new HashMap<>();
        if (mapping == null || mapping.isEmpty()) {
            vars.put("response", actualOutput);
            vars.put("actual_output", actualOutput);
            if (row != null) {
                vars.put("row", row);
                if (row.containsKey("reference")) {
                    vars.put("reference", row.get("reference"));
                }
            }
            vars.put("trace", trace);
            return vars;
        }
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            vars.put(e.getKey(), resolvePath(e.getValue(), row, actualOutput, trace));
        }
        return vars;
    }

    /**
     * Code 语义：始终注入基线变量，再由 mapping 覆盖同名键。
     */
    public static Map<String, Object> resolveForCode(Map<String, String> mapping,
                                                     Map<String, Object> row,
                                                     String actualOutput,
                                                     Object trace) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("row", row);
        vars.put("trace", trace);
        vars.put("actual_output", actualOutput);
        vars.put("response", actualOutput);
        if (row != null && row.containsKey("reference")) {
            vars.put("reference", row.get("reference"));
        }
        if (mapping != null) {
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                vars.put(e.getKey(), resolvePath(e.getValue(), row, actualOutput, trace));
            }
        }
        return vars;
    }

    /**
     * 解析单个数据源表达式。
     */
    public static Object resolvePath(String expr,
                                     Map<String, Object> row,
                                     String actualOutput,
                                     Object trace) {
        if (expr == null) {
            return null;
        }
        String e = expr.trim();
        if ("$actual_output".equals(e)) {
            return actualOutput;
        }
        if ("$trace".equals(e)) {
            return trace;
        }
        if (e.startsWith("$row.")) {
            String field = e.substring("$row.".length());
            return row == null ? null : row.get(field);
        }
        return e;
    }
}
