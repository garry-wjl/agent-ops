package ink.garry.rd.agent.ws.application.sandbox.lifecycle;

/**
 * 会话级沙箱状态机阶段。
 */
public enum SessionSandboxPhase {
    /** 尚未开始准备。 */
    IDLE,
    /** 正在 create / 等 Ready（含后台预热）。 */
    PREPARING,
    /** 已绑定可用实例。 */
    READY,
    /** 最近一次准备失败。 */
    FAILED
}
