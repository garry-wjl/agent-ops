package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

/**
 * 沙箱运行时实例状态：热池空闲或已绑定会话。
 */
public enum SandboxRuntimeStatus {

    /** 热池中待命，可被 claim。 */
    IDLE,

    /** 已绑定某一会话。 */
    BOUND
}
