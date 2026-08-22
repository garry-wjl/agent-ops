package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 评估器辅助：模板渲染、输出格式强制注入与模型响应解析（可单测）。
 */
public final class LlmGraderSupport {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    /** 注入段标记，避免重复追加。 */
    public static final String OUTPUT_FORMAT_MARKER = "<!--agentops-llm-grader-output-format-->";

    private LlmGraderSupport() {
    }

    /**
     * 将 promptTemplate 中 {@code {{var}}} 替换为 variables 对应值。
     *
     * @param template 提示词模板
     * @param variables 变量表
     * @return 渲染后的提示词
     */
    public static String renderTemplate(String template, Map<String, Object> variables) {
        if (StrUtil.isBlank(template)) {
            return "";
        }
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object val = vars.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : String.valueOf(val)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 按评估器分数区间动态生成输出格式约束（强制注入用）。
     *
     * @param scoreMin 最低分（空则 0）
     * @param scoreMax 最高分（空则 100）
     * @return 注入文案
     */
    public static String buildOutputFormatInstruction(BigDecimal scoreMin, BigDecimal scoreMax) {
        BigDecimal lo = scoreMin == null ? BigDecimal.ZERO : scoreMin;
        BigDecimal hi = scoreMax == null ? new BigDecimal("100") : scoreMax;
        if (lo.compareTo(hi) > 0) {
            BigDecimal tmp = lo;
            lo = hi;
            hi = tmp;
        }
        String loText = lo.stripTrailingZeros().toPlainString();
        String hiText = hi.stripTrailingZeros().toPlainString();
        return OUTPUT_FORMAT_MARKER + "\n"
                + "【系统强制输出格式】请只输出一个 JSON 对象，不要 Markdown 代码块，不要其它说明文字：\n"
                + "{\"score\": <数值>, \"reason\": \"<简短理由>\"}\n"
                + "其中 score 必须是 " + loText + " 到 " + hiText + " 之间的数字（含边界）。";
    }

    /**
     * 在用户 Prompt 末尾追加动态输出格式约束；已含标记则不重复追加。
     *
     * @param userPrompt 用户侧已渲染 Prompt
     * @param scoreMin 最低分
     * @param scoreMax 最高分
     * @return 最终发给模型的 Prompt
     */
    public static String appendOutputFormatInstruction(String userPrompt,
                                                       BigDecimal scoreMin,
                                                       BigDecimal scoreMax) {
        String body = userPrompt == null ? "" : userPrompt;
        if (body.contains(OUTPUT_FORMAT_MARKER)) {
            return body;
        }
        String instruction = buildOutputFormatInstruction(scoreMin, scoreMax);
        if (StrUtil.isBlank(body)) {
            return instruction;
        }
        return body.stripTrailing() + "\n\n" + instruction;
    }

    /**
     * 从模型返回文本解析 score 与 reason；优先 JSON，否则宽松提取数字。
     *
     * @param content 模型输出文本
     * @param scoreMin 分数下限
     * @param scoreMax 分数上限
     * @return 解析结果；score 为 null 表示解析失败
     */
    public static ParsedScore parseScoreResponse(String content, BigDecimal scoreMin, BigDecimal scoreMax) {
        if (StrUtil.isBlank(content)) {
            return new ParsedScore(null, "模型返回为空");
        }
        String trimmed = content.trim();
        JSONObject json = tryParseJson(trimmed);
        if (json != null) {
            BigDecimal score = toBigDecimal(json.get("score"));
            String reason = json.getString("reason");
            if (reason == null) {
                reason = json.getString("explanation");
            }
            if (score != null) {
                return new ParsedScore(clamp(score, scoreMin, scoreMax), reason);
            }
        }
        BigDecimal loose = extractLooseScore(trimmed);
        if (loose != null) {
            return new ParsedScore(clamp(loose, scoreMin, scoreMax), trimmed);
        }
        return new ParsedScore(null, "无法解析 score: " + abbreviate(trimmed, 200));
    }

    private static JSONObject tryParseJson(String text) {
        try {
            if (text.startsWith("{")) {
                return JSON.parseObject(text);
            }
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return JSON.parseObject(text.substring(start, end + 1));
            }
        } catch (Exception ignored) {
            // 宽松模式继续
        }
        return null;
    }

    private static BigDecimal extractLooseScore(String text) {
        Pattern p = Pattern.compile("(?:score|分数)\"?\\s*[:：]\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        Pattern num = Pattern.compile("\\b(0(?:\\.\\d+)?|1(?:\\.0+)?)\\b");
        m = num.matcher(text);
        if (m.find()) {
            return new BigDecimal(m.group(1));
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal clamp(BigDecimal score, BigDecimal min, BigDecimal max) {
        BigDecimal lo = min == null ? BigDecimal.ZERO : min;
        BigDecimal hi = max == null ? new BigDecimal("100") : max;
        if (score.compareTo(lo) < 0) {
            return lo;
        }
        if (score.compareTo(hi) > 0) {
            return hi;
        }
        return score;
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    /** LLM 评分解析结果。 */
    public record ParsedScore(BigDecimal score, String reason) {
    }
}
