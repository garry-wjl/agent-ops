package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CODE 评估器执行器：Spring SpEL 表达式，禁止 BeanResolver。
 * <p>根变量：response、reference、row、trace、actual_output。
 */
@Component
public class CodeGraderRunner {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 对绑定列表逐条 CODE 评分。
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
        Map<String, Object> vars = GraderVariableResolver.resolveForCode(
                binding.getMapping(), row, actualOutput, trace);
        Map<String, Object> cfg = binding.getConfigSnapshot() == null ? Map.of() : binding.getConfigSnapshot();
        Map<String, Object> merged = new HashMap<>(cfg);
        merged.putAll(vars);
        return evaluateOne(GraderKind.CODE.name(), null, cfg, merged);
    }

    /**
     * 试跑单条 CODE 评估器。
     */
    public ScoreResult evaluateOne(String kind, String builtinCode, Map<String, Object> config,
                                   Map<String, Object> variables) {
        if (!GraderKind.CODE.name().equalsIgnoreCase(kind)) {
            return fail("非 CODE 评估器 kind=" + kind);
        }
        Map<String, Object> cfg = config == null ? Map.of() : config;
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        String script = str(cfg.get("script"));
        if (StrUtil.isBlank(script)) {
            return fail("CODE 评估器缺少 script");
        }
        BigDecimal passThreshold = toBigDecimal(cfg.get("passThreshold"), new BigDecimal("0.5"));
        try {
            EvaluationContext ctx = buildContext(vars);
            Expression expr = parser.parseExpression(script);
            Object result = expr.getValue(ctx);
            return toScoreResult(result, passThreshold);
        } catch (Exception ex) {
            return fail("SpEL 执行失败: " + ex.getMessage());
        }
    }

    /**
     * 构建 SpEL 上下文（无 BeanResolver，仅变量绑定）。
     * <p>注入全部 resolved 变量（含自定义名）；并保证 response 在缺省时回退 actual_output。
     */
    private EvaluationContext buildContext(Map<String, Object> vars) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            if (e.getKey() != null) {
                ctx.setVariable(e.getKey(), e.getValue());
            }
        }
        String response = str(vars.get("response"));
        if (response == null) {
            response = str(vars.get("actual_output"));
            ctx.setVariable("response", response);
        }
        if (!vars.containsKey("actual_output") && vars.get("response") != null) {
            ctx.setVariable("actual_output", str(vars.get("response")));
        }
        return ctx;
    }

    /**
     * 将 SpEL 返回值转为 ScoreResult。
     */
    ScoreResult toScoreResult(Object result, BigDecimal passThreshold) {
        if (result instanceof Boolean b) {
            return ScoreResult.builder()
                    .score(b ? BigDecimal.ONE : BigDecimal.ZERO)
                    .passed(b)
                    .explanation(b ? "表达式为 true" : "表达式为 false")
                    .build();
        }
        if (result instanceof Number n) {
            BigDecimal score = BigDecimal.valueOf(n.doubleValue());
            boolean passed = score.compareTo(passThreshold) >= 0;
            return ScoreResult.builder()
                    .score(score)
                    .passed(passed)
                    .explanation("数值 score=" + score)
                    .build();
        }
        return fail("表达式必须返回 boolean 或 Number，实际=" + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    private static ScoreResult fail(String explanation) {
        return ScoreResult.builder()
                .score(BigDecimal.ZERO)
                .passed(false)
                .explanation(explanation)
                .build();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal toBigDecimal(Object o, BigDecimal def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception ex) {
            return def;
        }
    }
}
