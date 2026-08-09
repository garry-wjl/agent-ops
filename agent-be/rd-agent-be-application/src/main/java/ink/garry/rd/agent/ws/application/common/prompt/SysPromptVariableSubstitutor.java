package ink.garry.rd.agent.ws.application.common.prompt;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统提示词变量替换工具：校验调用上下文、浅合并多层变量、按 {@code {{key}}} 替换模板。
 * <p>
 * 纯函数工具，无 Spring 依赖；缺失变量保留原文，不递归替换。
 */
public final class SysPromptVariableSubstitutor {

    /** 单 value 字符串化后 UTF-8 字节上限 */
    public static final int MAX_VALUE_BYTES = 2048;
    /** 整体 JSON 序列化 UTF-8 字节上限 */
    public static final int MAX_CONTEXT_BYTES = 16384;

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)\\}\\}");

    private SysPromptVariableSubstitutor() {}

    /**
     * 校验 context：键名、值类型（string/number/boolean）、单值与整体大小。
     * {@code null} 或空 Map 视为合法（跳过）。
     */
    public static void validateContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> e : context.entrySet()) {
            String key = e.getKey();
            if (key == null || !KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "context 键名非法: " + key);
            }
            Object value = e.getValue();
            if (value != null && !(value instanceof String)
                    && !(value instanceof Number)
                    && !(value instanceof Boolean)) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "context 值类型非法，仅支持 string/number/boolean: " + key);
            }
            String asString = value == null ? "null" : String.valueOf(value);
            if (asString.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "context 单键值超长(≤2KB): " + key);
            }
        }
        String json = JSON.toJSONString(context);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "context 整体超长(≤16KB)");
        }
    }

    /**
     * 浅合并多层变量；后层覆盖前层；value → String（null → "null"）。
     */
    @SafeVarargs
    public static Map<String, String> merge(Map<String, ?>... layers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (layers == null) {
            return out;
        }
        for (Map<String, ?> layer : layers) {
            if (layer == null || layer.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, ?> e : layer.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                Object v = e.getValue();
                out.put(e.getKey(), v == null ? "null" : String.valueOf(v));
            }
        }
        return out;
    }

    /**
     * 按 {@code {{key}}} 替换；未命中保留原文；不递归。
     */
    public static String substitute(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        if (StrUtil.isEmpty(template) || vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = vars.containsKey(key) ? vars.get(key) : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 内置变量（全大写键）；空入参写空串。
     */
    public static Map<String, String> builtinVars(String sessionNum, String agentNum,
                                                  String agentVersionNum, String workspaceNum,
                                                  String operatorId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SESSION_NUM", nullToEmpty(sessionNum));
        m.put("AGENT_NUM", nullToEmpty(agentNum));
        m.put("AGENT_VERSION_NUM", nullToEmpty(agentVersionNum));
        m.put("WORKSPACE_NUM", nullToEmpty(workspaceNum));
        m.put("OPERATOR_ID", nullToEmpty(operatorId));
        return m;
    }

    /** 将 context Map 序列化为 JSON object 字符串；null/空 → null。 */
    public static String toJson(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(context);
    }

    /** 解析会话已存 JSON；非法或空 → 空 Map。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJson(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = JSON.parse(json);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** 浅合并两个 Object Map（后覆盖前），用于会话持久化。 */
    public static Map<String, Object> shallowMergeObjects(Map<String, Object> base,
                                                          Map<String, Object> patch) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) {
            out.putAll(base);
        }
        if (patch != null) {
            out.putAll(patch);
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
