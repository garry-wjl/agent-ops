package ink.garry.rd.agent.ws.domain.evaluation.valueobject;

/**
 * 评测任务状态枚举：标识一次评测任务整体的生命周期。
 * 状态机：PENDING → RUNNING → FINISHED / FAILED。
 */
public enum EvaluationStatus {
    /** 待开始：评测任务已创建、尚未派发。 */
    PENDING,
    /** 执行中：用例正在批量执行。 */
    RUNNING,
    /** 已完成：所有用例执行结束（个别失败不影响整体 FINISHED）。 */
    FINISHED,
    /** 已失败：评测任务整体失败（如调度异常）。 */
    FAILED
}
