package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * SSE 平台统一事件类型（5 类）。
 * <p>
 * 所有 Runner 输出的事件流必须落入这 5 类之一，对外协议形态见 PlatformEvent。
 */
public enum EventType {
    /** 模型增量输出（流式 token） */
    MESSAGE_DELTA("message.delta"),
    /** 一个推理步骤开始（如 Skill 调用前） */
    STEP_START("step.start"),
    /** 一个推理步骤结束（含状态、耗时） */
    STEP_END("step.end"),
    /** 调用结束（含会话级汇总信息） */
    FINAL("final"),
    /** 异常（含错误码、消息、可选 stepId） */
    ERROR("error");

    /** 对外暴露的事件 code（与协议一致，使用点分小写） */
    private final String code;

    EventType(String code) {
        this.code = code;
    }

    /** 返回对外协议事件 code */
    public String code() {
        return code;
    }
}
