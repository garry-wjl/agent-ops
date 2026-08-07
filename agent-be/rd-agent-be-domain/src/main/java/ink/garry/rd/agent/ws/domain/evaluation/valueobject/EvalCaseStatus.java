package ink.garry.rd.agent.ws.domain.evaluation.valueobject;

/**
 * 评测执行用例状态枚举：标识单条用例在评测过程中的生命周期。
 */
public enum EvalCaseStatus {
    /** 待执行：用例已生成、尚未开始。 */
    PENDING,
    /** 执行中：用例已下发到执行器并在跑。 */
    RUNNING,
    /** 已通过：执行成功且 Judge 评分通过。 */
    PASSED,
    /** 已失败：执行失败或 Judge 评分不通过。 */
    FAILED
}
