package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.collection.CollUtil;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评估器引擎门面：按 kind 分发至 Builtin / LLM / CODE 执行器。
 */
@Primary
@Component
public class CompositeGraderEngine implements GraderEngine {

    @Resource
    private BuiltinGraderEngine builtinGraderEngine;
    @Resource
    private LlmGraderRunner llmGraderRunner;
    @Resource
    private CodeGraderRunner codeGraderRunner;

    @Override
    public List<ScoreResult> evaluateAll(List<GraderBindingSnapshot> bindings,
                                         Map<String, Object> row,
                                         String actualOutput,
                                         Object trace) {
        List<ScoreResult> results = new ArrayList<>();
        if (CollUtil.isEmpty(bindings)) {
            return results;
        }
        for (GraderBindingSnapshot b : bindings) {
            ScoreResult one = dispatchBinding(b, row, actualOutput, trace);
            one.setGraderNum(b.getGraderNum());
            one.setGraderVersion(b.getGraderVersion());
            results.add(one);
        }
        return results;
    }

    @Override
    public ScoreResult evaluateOne(String kind, String builtinCode, Map<String, Object> config,
                                   Map<String, Object> variables) {
        GraderKind k = resolveKind(kind);
        return switch (k) {
            case BUILTIN -> builtinGraderEngine.evaluateOne(kind, builtinCode, config, variables);
            case LLM -> llmGraderRunner.evaluateOne(kind, builtinCode, config, variables);
            case CODE -> codeGraderRunner.evaluateOne(kind, builtinCode, config, variables);
        };
    }

    private ScoreResult dispatchBinding(GraderBindingSnapshot binding,
                                        Map<String, Object> row,
                                        String actualOutput,
                                        Object trace) {
        GraderKind k = resolveKind(binding.getKind());
        return switch (k) {
            case BUILTIN -> builtinGraderEngine.evaluateBinding(binding, row, actualOutput, trace);
            case LLM -> llmGraderRunner.evaluateBinding(binding, row, actualOutput, trace);
            case CODE -> codeGraderRunner.evaluateBinding(binding, row, actualOutput, trace);
        };
    }

    private GraderKind resolveKind(String kind) {
        if (kind == null) {
            return GraderKind.BUILTIN;
        }
        try {
            return GraderKind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return GraderKind.BUILTIN;
        }
    }
}
