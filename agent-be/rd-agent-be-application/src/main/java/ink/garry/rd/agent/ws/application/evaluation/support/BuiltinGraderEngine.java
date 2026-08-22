package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.BuiltinGraderCode;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置规则评估引擎：NON_EMPTY / EXACT_MATCH / CONTAINS / JSON_VALID / TOOL_*。
 * <p>由 {@link CompositeGraderEngine} 按 kind=BUILTIN 调用，不直接实现 {@link GraderEngine}。
 */
@Component
public class BuiltinGraderEngine {

    /**
     * 对全部 BUILTIN 绑定评分。
     */
    public List<ScoreResult> evaluateAll(List<GraderBindingSnapshot> bindings,
                                         Map<String, Object> row,
                                         String actualOutput,
                                         Object trace) {
        List<ScoreResult> results = new ArrayList<>();
        if (CollUtil.isEmpty(bindings)) {
            return results;
        }
        for (GraderBindingSnapshot b : bindings) {
            ScoreResult one = evaluateBinding(b, row, actualOutput, trace);
            one.setGraderNum(b.getGraderNum());
            one.setGraderVersion(b.getGraderVersion());
            results.add(one);
        }
        return results;
    }

    /**
     * 单绑定评分。
     */
    public ScoreResult evaluateBinding(GraderBindingSnapshot binding,
                                       Map<String, Object> row,
                                       String actualOutput,
                                       Object trace) {
        Map<String, Object> vars = GraderVariableResolver.resolve(
                binding.getMapping(), row, actualOutput, trace);
        Map<String, Object> config = binding.getConfigSnapshot() == null ? Map.of() : binding.getConfigSnapshot();
        Map<String, Object> merged = new HashMap<>(config);
        merged.putAll(vars);
        return evaluateOne(
                binding.getKind() == null ? GraderKind.BUILTIN.name() : binding.getKind(),
                binding.getBuiltinCode(),
                merged,
                merged);
    }

    /**
     * 单次评分（试跑 / Composite 分发）。
     */
    public ScoreResult evaluateOne(String kind, String builtinCode, Map<String, Object> config,
                                   Map<String, Object> variables) {
        if (!GraderKind.BUILTIN.name().equalsIgnoreCase(kind)) {
            return ScoreResult.builder()
                    .score(BigDecimal.ZERO)
                    .passed(false)
                    .explanation("BuiltinGraderEngine 仅处理 BUILTIN，kind=" + kind)
                    .build();
        }
        if (!BuiltinGraderCode.isValid(builtinCode)) {
            return ScoreResult.builder()
                    .score(BigDecimal.ZERO)
                    .passed(false)
                    .explanation("未知 builtinCode=" + builtinCode)
                    .build();
        }
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        Map<String, Object> cfg = config == null ? Map.of() : config;
        return switch (BuiltinGraderCode.valueOf(builtinCode)) {
            case NON_EMPTY -> nonEmpty(vars);
            case EXACT_MATCH -> exactMatch(vars, cfg);
            case CONTAINS -> contains(vars, cfg);
            case JSON_VALID -> jsonValid(vars);
            case TOOL_CALLED -> toolCalled(vars, cfg);
            case TOOL_NAME_CONTAINS -> toolNameContains(vars, cfg);
        };
    }

    private ScoreResult nonEmpty(Map<String, Object> vars) {
        String response = str(vars.get("response"));
        boolean ok = StrUtil.isNotBlank(response);
        return ScoreResult.builder()
                .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                .passed(ok)
                .explanation(ok ? "输出非空" : "输出为空")
                .build();
    }

    private ScoreResult exactMatch(Map<String, Object> vars, Map<String, Object> cfg) {
        String response = str(vars.get("response"));
        String reference = str(vars.containsKey("reference") ? vars.get("reference") : cfg.get("reference"));
        boolean trim = bool(cfg.get("trim"), true);
        boolean ignoreCase = bool(cfg.get("ignoreCase"), false);
        String left = response == null ? "" : response;
        String right = reference == null ? "" : reference;
        if (trim) {
            left = left.trim();
            right = right.trim();
        }
        boolean ok = ignoreCase ? left.equalsIgnoreCase(right) : left.equals(right);
        return ScoreResult.builder()
                .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                .passed(ok)
                .explanation(ok ? "精确匹配" : "与 reference 不匹配")
                .build();
    }

    @SuppressWarnings("unchecked")
    private ScoreResult contains(Map<String, Object> vars, Map<String, Object> cfg) {
        String response = str(vars.get("response"));
        if (response == null) {
            response = "";
        }
        List<String> keywords = new ArrayList<>();
        Object kw = vars.containsKey("keywords") ? vars.get("keywords") : cfg.get("keywords");
        if (kw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    keywords.add(String.valueOf(o));
                }
            }
        } else if (kw instanceof String s && StrUtil.isNotBlank(s)) {
            if (s.trim().startsWith("[")) {
                JSONArray arr = JSON.parseArray(s);
                for (int i = 0; i < arr.size(); i++) {
                    keywords.add(arr.getString(i));
                }
            } else {
                keywords.add(s);
            }
        } else if (kw instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                keywords.add(arr.getString(i));
            }
        }
        if (keywords.isEmpty()) {
            return ScoreResult.builder()
                    .score(BigDecimal.ZERO)
                    .passed(false)
                    .explanation("未提供 keywords")
                    .build();
        }
        boolean ok = true;
        List<String> missing = new ArrayList<>();
        for (String k : keywords) {
            if (!response.contains(k)) {
                ok = false;
                missing.add(k);
            }
        }
        return ScoreResult.builder()
                .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                .passed(ok)
                .explanation(ok ? "包含全部关键词" : "缺少关键词: " + missing)
                .build();
    }

    private ScoreResult jsonValid(Map<String, Object> vars) {
        String response = str(vars.get("response"));
        if (StrUtil.isBlank(response)) {
            return ScoreResult.builder().score(BigDecimal.ZERO).passed(false).explanation("输出为空").build();
        }
        try {
            Object parsed = JSON.parse(response.trim());
            boolean ok;
            if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray)) {
                String t = response.trim();
                if (t.startsWith("{")) {
                    JSON.parseObject(t);
                    ok = true;
                } else if (t.startsWith("[")) {
                    JSON.parseArray(t);
                    ok = true;
                } else {
                    ok = false;
                }
            } else {
                ok = true;
            }
            return ScoreResult.builder()
                    .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                    .passed(ok)
                    .explanation(ok ? "合法 JSON" : "不是 JSON 对象/数组")
                    .build();
        } catch (Exception ex) {
            return ScoreResult.builder()
                    .score(BigDecimal.ZERO)
                    .passed(false)
                    .explanation("JSON 解析失败: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * 检查 trace 中是否存在工具调用。
     */
    private ScoreResult toolCalled(Map<String, Object> vars, Map<String, Object> cfg) {
        Object traceObj = vars.containsKey("trace") ? vars.get("trace") : cfg.get("trace");
        List<String> toolNames = extractToolNames(traceObj);
        boolean ok = !toolNames.isEmpty();
        return ScoreResult.builder()
                .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                .passed(ok)
                .explanation(ok ? "存在工具调用: " + toolNames : "轨迹中无工具调用")
                .build();
    }

    /**
     * 检查 toolNames 是否包含关键词。
     */
    private ScoreResult toolNameContains(Map<String, Object> vars, Map<String, Object> cfg) {
        String keyword = str(vars.containsKey("keyword") ? vars.get("keyword") : cfg.get("keyword"));
        if (StrUtil.isBlank(keyword)) {
            return ScoreResult.builder()
                    .score(BigDecimal.ZERO)
                    .passed(false)
                    .explanation("未提供 keyword")
                    .build();
        }
        Object traceObj = vars.containsKey("trace") ? vars.get("trace") : cfg.get("trace");
        List<String> toolNames = extractToolNames(traceObj);
        boolean ok = toolNames.stream().anyMatch(n -> n != null && n.contains(keyword));
        return ScoreResult.builder()
                .score(ok ? BigDecimal.ONE : BigDecimal.ZERO)
                .passed(ok)
                .explanation(ok ? "工具名含 " + keyword : "工具名均不含 " + keyword + ", names=" + toolNames)
                .build();
    }

    /**
     * 从 trace（Map/JSON 字符串）提取 toolNames。
     */
    @SuppressWarnings("unchecked")
    List<String> extractToolNames(Object trace) {
        List<String> names = new ArrayList<>();
        if (trace == null) {
            return names;
        }
        JSONObject obj = null;
        if (trace instanceof Map<?, ?> map) {
            obj = new JSONObject();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                obj.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else if (trace instanceof String s && StrUtil.isNotBlank(s)) {
            try {
                obj = JSON.parseObject(s);
            } catch (Exception ignored) {
                return names;
            }
        } else if (trace instanceof JSONObject jo) {
            obj = jo;
        }
        if (obj == null) {
            return names;
        }
        Object toolNames = obj.get("toolNames");
        if (toolNames instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    names.add(String.valueOf(o));
                }
            }
        } else if (toolNames instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                names.add(arr.getString(i));
            }
        }
        Object tools = obj.get("tools");
        if (tools instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object n = m.get("name");
                    if (n != null) {
                        names.add(String.valueOf(n));
                    }
                } else if (o instanceof JSONObject jo) {
                    String n = jo.getString("name");
                    if (n != null) {
                        names.add(n);
                    }
                }
            }
        }
        return names;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Object o, boolean def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
