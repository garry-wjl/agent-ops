package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import io.agentscope.harness.agent.sandbox.SandboxState;

/**
 * OpenSandbox 后端的可序列化沙箱状态。
 * <p>
 * 容器由平台资产供给（{@code sandboxRef → instanceId}），Harness 侧只记录连接参数与 workspace 就绪标记；
 * Jackson 类型名为 {@code opensandbox}（由 {@link OpenSandboxClient} 注册 {@code NamedType}）。
 */
public class OpenSandboxSandboxState extends SandboxState {

    /** OpenSandbox 容器实例 id（平台已供给）。 */
    private String instanceId;

    /** 平台会话编号，用于 bash session 复用。 */
    private String sessionNum;

    /** 会话环境变量快照（可空）。 */
    private java.util.Map<String, String> env;

    /** session 映射滑动 TTL（分钟）。 */
    private long ttlMinutes = 30L;

    /** 容器内 workspace 根路径，默认 {@code /workspace}。 */
    private String workspaceRoot = "/workspace";

    /**
     * 是否由本 SPI 拥有容器生命周期。
     * <p>
     * 平台供给场景应为 {@code false}：{@code shutdown} 不 kill 远程容器。
     */
    private boolean containerOwned = false;

    /**
     * @return 容器实例 id
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * @param instanceId 容器实例 id
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * @return 会话编号
     */
    public String getSessionNum() {
        return sessionNum;
    }

    /**
     * @param sessionNum 会话编号
     */
    public void setSessionNum(String sessionNum) {
        this.sessionNum = sessionNum;
    }

    /**
     * @return 环境变量
     */
    public java.util.Map<String, String> getEnv() {
        return env;
    }

    /**
     * @param env 环境变量
     */
    public void setEnv(java.util.Map<String, String> env) {
        this.env = env;
    }

    /**
     * @return TTL 分钟数
     */
    public long getTtlMinutes() {
        return ttlMinutes;
    }

    /**
     * @param ttlMinutes TTL 分钟数
     */
    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * @return workspace 根路径
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * @param workspaceRoot workspace 根路径
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * @return 是否由本 SPI 拥有容器生命周期
     */
    public boolean isContainerOwned() {
        return containerOwned;
    }

    /**
     * @param containerOwned 是否拥有容器生命周期
     */
    public void setContainerOwned(boolean containerOwned) {
        this.containerOwned = containerOwned;
    }
}
