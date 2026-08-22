package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import ink.garry.rd.agent.ws.infra.model.gateway.ModelCredential;
import ink.garry.rd.agent.ws.infra.model.gateway.ModelCredentialResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 评估器执行器：调用 OpenAI 兼容 chat/completions 非流式接口打分。
 */
@Slf4j
@Component
public class LlmGraderRunner {

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    @Value("${app.evaluation.llm.timeout-ms:30000}")
    private int defaultTimeoutMs;

    @Resource
    private ModelCredentialResolver modelCredentialResolver;

    /**
     * 对绑定列表逐条 LLM 评分（Composite 按 kind 分发时通常单条调用 evaluateBinding）。
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
     * 单绑定评分（任务跑批）。
     */
    public ScoreResult evaluateBinding(GraderBindingSnapshot binding,
                                       Map<String, Object> row,
                                       String actualOutput,
                                       Object trace) {
        Map<String, Object> vars = GraderVariableResolver.resolve(
                binding.getMapping(), row, actualOutput, trace);
        Map<String, Object> cfg = binding.getConfigSnapshot() == null ? Map.of() : binding.getConfigSnapshot();
        Map<String, Object> merged = new HashMap<>(cfg);
        merged.putAll(vars);
        return evaluateOne(GraderKind.LLM.name(), null, cfg, merged);
    }

    /**
     * 试跑单条 LLM 评估器。
     */
    public ScoreResult evaluateOne(String kind, String builtinCode, Map<String, Object> config,
                                   Map<String, Object> variables) {
        if (!GraderKind.LLM.name().equalsIgnoreCase(kind)) {
            return fail("非 LLM 评估器 kind=" + kind);
        }
        Map<String, Object> cfg = config == null ? Map.of() : config;
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        String modelNum = str(cfg.get("modelNum"));
        String promptTemplate = str(cfg.get("promptTemplate"));
        if (StrUtil.isBlank(modelNum) || StrUtil.isBlank(promptTemplate)) {
            return fail("LLM 评估器缺少 modelNum 或 promptTemplate");
        }
        BigDecimal scoreMin = toBigDecimal(cfg.get("scoreMin"), BigDecimal.ZERO);
        BigDecimal scoreMax = toBigDecimal(cfg.get("scoreMax"), new BigDecimal("100"));
        BigDecimal passThreshold = toBigDecimal(cfg.get("passThreshold"), new BigDecimal("60"));

        String prompt = LlmGraderSupport.appendOutputFormatInstruction(
                LlmGraderSupport.renderTemplate(promptTemplate, vars),
                scoreMin,
                scoreMax);
        try {
            ModelCredential credential = modelCredentialResolver.resolve(modelNum);
            String content = invokeChat(credential, prompt, resolveTimeout(cfg));
            LlmGraderSupport.ParsedScore parsed = LlmGraderSupport.parseScoreResponse(content, scoreMin, scoreMax);
            if (parsed.score() == null) {
                return fail(parsed.reason());
            }
            boolean passed = parsed.score().compareTo(passThreshold) >= 0;
            return ScoreResult.builder()
                    .score(parsed.score())
                    .passed(passed)
                    .explanation(parsed.reason())
                    .build();
        } catch (Exception ex) {
            log.warn("LLM grader invoke failed modelNum={}: {}", modelNum, ex.getMessage());
            return fail("LLM 调用失败: " + ex.getMessage());
        }
    }

    private String invokeChat(ModelCredential credential, String prompt, int timeoutMs) {
        String base = credential.baseUrl();
        if (StrUtil.isBlank(base)) {
            throw new IllegalStateException("模型 baseUrl 为空");
        }
        String url = base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions";
        JSONObject body = new JSONObject();
        body.put("model", credential.modelId());
        body.put("stream", false);
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", prompt);
        messages.add(msg);
        body.put("messages", messages);

        HttpResponse resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + credential.apiKey())
                .header("Content-Type", "application/json")
                .body(body.toJSONString())
                .timeout(timeoutMs)
                .execute();
        if (!resp.isOk()) {
            throw new IllegalStateException("HTTP " + resp.getStatus() + ": " + resp.body());
        }
        JSONObject json = JSON.parseObject(resp.body());
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("模型响应无 choices");
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        if (message == null) {
            return choice.getString("text");
        }
        return message.getString("content");
    }

    private int resolveTimeout(Map<String, Object> cfg) {
        Object t = cfg.get("timeoutMs");
        if (t instanceof Number n) {
            return n.intValue();
        }
        return defaultTimeoutMs > 0 ? defaultTimeoutMs : DEFAULT_TIMEOUT_MS;
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
