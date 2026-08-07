package ink.garry.rd.agent.ws.domain.session.valueobject;

/**
 * 调用链终态枚举：标识一次 Agent invoke 的最终结果。
 */
public enum InvocationStatus {
    /** 调用成功完成。 */
    SUCCESS,
    /** 调用失败（异常、超时、被拒绝等）。 */
    FAILED,
    /** 调用被截断（如达到最大步数/最大 token 限制提前终止）。 */
    TRUNCATED
}
