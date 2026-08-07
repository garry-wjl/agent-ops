package ink.garry.rd.agent.ws.facade.common;

/**
 * 平台流式事件类型。
 * 每个枚举值通过 {@link #code()} 暴露 OpenAI / SSE 兼容的事件名，便于前端与下游统一识别。
 */
public enum EventType {
    /** 消息文本增量（流式 token / 片段） */
    MESSAGE_DELTA("message.delta"),
    /** Agent 推理步骤开始 */
    STEP_START("step.start"),
    /** Agent 推理步骤结束 */
    STEP_END("step.end"),
    /** 最终完成事件 */
    FINAL("final"),
    /** 异常事件 */
    ERROR("error");

    /** 事件代号，作为 SSE event 字段或前端协议字段使用 */
    private final String code;

    EventType(String code) {
        this.code = code;
    }

    /**
     * 返回事件代号字符串。
     *
     * @return 事件代号（如 "message.delta"）
     */
    public String code() {
        return code;
    }
}
