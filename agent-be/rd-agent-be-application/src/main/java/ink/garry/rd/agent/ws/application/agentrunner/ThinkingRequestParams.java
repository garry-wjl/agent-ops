package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按模型 Base URL 写入厂商的深度思考开关。
 * <p>
 * 只对已识别的厂商下发参数。未识别的厂商在开启时保持现状；关闭时直接失败，避免把不认识的字段发给模型。
 */
public final class ThinkingRequestParams {

    private ThinkingRequestParams() {
    }

    /**
     * 生成需要合并进 {@code GenerateOptions.additionalBodyParams} 的字段。
     *
     * @param baseUrl 模型服务地址
     * @param enabled 是否打开深度思考
     * @return 额外请求体；无字段时返回空 Map
     */
    public static Map<String, Object> bodyParams(String baseUrl, boolean enabled) {
        String host = StrUtil.blankToDefault(baseUrl, "").toLowerCase(Locale.ROOT);
        if (isDeepSeek(host) || isGlm(host) || isKimi(host)) {
            return Map.of("thinking", typeBody(enabled ? "enabled" : "disabled"));
        }
        if (isMiniMax(host)) {
            return Map.of("thinking", typeBody(enabled ? "adaptive" : "disabled"));
        }
        if (isDashScope(host)) {
            return Map.of("enable_thinking", enabled);
        }
        if (!enabled) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "当前模型不支持关闭深度思考");
        }
        return Map.of();
    }

    private static Map<String, Object> typeBody(String type) {
        Map<String, Object> thinking = new LinkedHashMap<>();
        thinking.put("type", type);
        return thinking;
    }

    private static boolean isDeepSeek(String host) {
        return host.contains("deepseek");
    }

    private static boolean isMiniMax(String host) {
        return host.contains("minimax") || host.contains("minimaxi");
    }

    private static boolean isGlm(String host) {
        return host.contains("bigmodel") || host.contains("zhipu") || host.contains("z.ai");
    }

    private static boolean isKimi(String host) {
        return host.contains("moonshot");
    }

    private static boolean isDashScope(String host) {
        return host.contains("dashscope") || host.contains("aliyuncs");
    }
}
