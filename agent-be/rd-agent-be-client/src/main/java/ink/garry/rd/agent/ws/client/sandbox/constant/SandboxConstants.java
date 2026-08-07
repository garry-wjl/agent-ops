package ink.garry.rd.agent.ws.client.sandbox.constant;

/**
 * 沙箱管理常量（与 domain {@code Sandbox} 不变量、DDL 约束保持一致）。
 */
public final class SandboxConstants {

    private SandboxConstants() {
    }

    /** 名称长度上限 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 备注长度上限 */
    public static final int REMARK_MAX_LENGTH = 100;

    /** CPU 下限（核） */
    public static final String CPU_MIN = "0.5";

    /** CPU 上限（核） */
    public static final String CPU_MAX = "16";

    /** CPU 步进单位（核） */
    public static final String CPU_STEP = "0.5";

    /** 内存下限（MB） */
    public static final int MEMORY_MIN = 128;

    /** 内存上限（MB） */
    public static final int MEMORY_MAX = 65536;

    /** 容器存活时间下限（分钟） */
    public static final int ALIVE_MIN = 1;

    /** 容器存活时间上限（分钟，24h） */
    public static final int ALIVE_MAX = 1440;
}
