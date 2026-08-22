package ink.garry.rd.agent.ws.domain.evaluation.task.valueobject;

/**
 * 评测任务绑定模式。
 */
public enum BindMode {
    /** 调用 Agent 产出 actual_output */
    AGENT,
    /** 不关联 Agent（P1 纯标注） */
    NONE
}
