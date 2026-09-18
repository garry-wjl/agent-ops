package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenSandbox Harness 客户端选项：描述已供给容器与会话绑定参数。
 * <p>
 * type 判别值为 {@code opensandbox}。{@link #createClient()} 需要事先注入
 * {@link OpenSandboxExecBridge}（通常经 {@link OpenSandboxFilesystemSpec#sessionRunner}）。
 */
public class OpenSandboxClientOptions extends SandboxClientOptions {

    /** 类型判别值。 */
    public static final String TYPE = "opensandbox";

    /** 已供给的 OpenSandbox 容器实例 id。 */
    private String instanceId;

    /** 平台会话编号。 */
    private String sessionNum;

    /** 会话环境变量。 */
    private Map<String, String> env = new LinkedHashMap<>();

    /** session 映射滑动 TTL（分钟），默认 30。 */
    private long ttlMinutes = 30L;

    /** 容器内 workspace 根，默认 {@code /workspace}。 */
    private String workspaceRoot = "/workspace";

    /**
     * 装配依赖：会话执行桥（不参与 JSON 序列化语义，由 Spec / Options 持有）。
     */
    private transient OpenSandboxExecBridge execBridge;

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 创建绑定了 {@link #execBridge} 的 {@link OpenSandboxClient}。
     *
     * @return OpenSandbox Harness 客户端
     * @throws SandboxException.SandboxConfigurationException 未注入 bridge 时
     */
    @Override
    public SandboxClient<OpenSandboxClientOptions> createClient() {
        if (execBridge == null) {
            throw new SandboxException.SandboxConfigurationException(
                    "OpenSandboxExecBridge is required; wire via OpenSandboxFilesystemSpec.sessionRunner(...)");
        }
        return new OpenSandboxClient(execBridge);
    }

    @Override
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * @return 容器实例 id
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * 设置容器实例 id。
     *
     * @param instanceId 已供给实例 id
     * @return this
     */
    public OpenSandboxClientOptions instanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    /**
     * @param instanceId 已供给实例 id
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
     * 设置会话编号。
     *
     * @param sessionNum 平台会话编号
     * @return this
     */
    public OpenSandboxClientOptions sessionNum(String sessionNum) {
        this.sessionNum = sessionNum;
        return this;
    }

    /**
     * @param sessionNum 平台会话编号
     */
    public void setSessionNum(String sessionNum) {
        this.sessionNum = sessionNum;
    }

    /**
     * @return 环境变量
     */
    public Map<String, String> getEnv() {
        return env;
    }

    /**
     * 设置环境变量。
     *
     * @param env 环境变量（可空）
     * @return this
     */
    public OpenSandboxClientOptions env(Map<String, String> env) {
        setEnv(env);
        return this;
    }

    /**
     * @param env 环境变量（可空）
     */
    public void setEnv(Map<String, String> env) {
        this.env = env != null ? new LinkedHashMap<>(env) : new LinkedHashMap<>();
    }

    /**
     * @return TTL 分钟数
     */
    public long getTtlMinutes() {
        return ttlMinutes;
    }

    /**
     * 设置 session TTL。
     *
     * @param ttlMinutes 分钟
     * @return this
     */
    public OpenSandboxClientOptions ttlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
        return this;
    }

    /**
     * @param ttlMinutes 分钟
     */
    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * 设置 workspace 根路径。
     *
     * @param workspaceRoot 容器内绝对路径
     * @return this
     */
    public OpenSandboxClientOptions workspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        return this;
    }

    /**
     * @param workspaceRoot 容器内绝对路径
     */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * @return 执行桥
     */
    public OpenSandboxExecBridge getExecBridge() {
        return execBridge;
    }

    /**
     * 注入会话执行桥。
     *
     * @param execBridge 应用层实现
     * @return this
     */
    public OpenSandboxClientOptions execBridge(OpenSandboxExecBridge execBridge) {
        this.execBridge = execBridge;
        return this;
    }

    /**
     * @param execBridge 应用层实现
     */
    public void setExecBridge(OpenSandboxExecBridge execBridge) {
        this.execBridge = execBridge;
    }
}
