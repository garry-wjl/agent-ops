package ink.garry.rd.agent.ws.application.agent;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 结构化输出与深度思考在配置保存时的归一化与校验。
 */
public final class StructuredOutputRules {

    /** JSON Schema 文本上限：64KB。 */
    public static final int MAX_SCHEMA_BYTES = 64 * 1024;

    public static final String STREAM_REJECTED =
            "该 Agent 已开启结构化输出，请调用 invokeJson";

    public static final String JSON_REJECTED =
            "该 Agent 未开启结构化输出";

    public static final String SCHEMA_INVALID =
            "模型没有返回符合 Schema 的 JSON";

    private StructuredOutputRules() {
    }

    /**
     * 补默认值并校验互斥与 Schema。
     *
     * @param snapshot 待保存的配置快照
     */
    public static void apply(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        boolean structured = Boolean.TRUE.equals(snapshot.getEnableStructuredOutput());
        snapshot.setEnableStructuredOutput(structured);
        if (snapshot.getEnableThinking() == null) {
            snapshot.setEnableThinking(true);
        }
        if (structured && Boolean.TRUE.equals(snapshot.getEnableUserFormInput())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "结构化输出与表单填写不能同时开启");
        }
        if (!structured) {
            return;
        }
        validateSchema(snapshot.getJsonSchema());
    }

    /**
     * 校验根类型为 object 且体积不超过 {@link #MAX_SCHEMA_BYTES}。
     *
     * @param schema JSON Schema
     */
    public static void validateSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "请填写 JSON Schema");
        }
        Object type = schema.get("type");
        if (!"object".equals(type)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "JSON Schema 根类型必须是 object");
        }
        byte[] bytes = JSON.toJSONString(schema).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SCHEMA_BYTES) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "JSON Schema 不能超过 64KB");
        }
    }

    /**
     * 运行时是否开启结构化输出。
     *
     * @param enabled 快照字段，null 视为关闭
     * @return 是否开启
     */
    public static boolean enabled(Boolean enabled) {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 运行时是否请求深度思考。null 视为打开。
     *
     * @param enableThinking 快照字段
     * @return 是否打开
     */
    public static boolean thinkingOn(Boolean enableThinking) {
        return !Boolean.FALSE.equals(enableThinking);
    }
}
