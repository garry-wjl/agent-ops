package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxClientOptions;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import java.util.Map;

/**
 * OpenSandbox 版 {@link SandboxFilesystemSpec}：供 {@code HarnessAgent.builder().filesystem(...)} 装配。
 * <p>
 * 默认 {@link IsolationScope#SESSION} + {@link NoopSnapshotSpec}。必须注入
 * {@link #sessionRunner(OpenSandboxExecBridge)} 或 {@link #client(SandboxClient)}。
 */
public class OpenSandboxFilesystemSpec extends SandboxFilesystemSpec {

    /** 显式注入的 Harness 客户端（优先于 options.createClient）。 */
    private SandboxClient<?> client;

    /** 客户端选项（含 instanceId / sessionNum / bridge）。 */
    private final OpenSandboxClientOptions options = new OpenSandboxClientOptions();

    /** 快照策略，默认 Noop。 */
    private SandboxSnapshotSpec snapshotSpec = new NoopSnapshotSpec();

    /** 默认 workspace 清单。 */
    private WorkspaceSpec defaultWorkspaceSpec = new WorkspaceSpec();

    /** 构造：隔离域默认 SESSION。 */
    public OpenSandboxFilesystemSpec() {
        isolationScope(IsolationScope.SESSION);
    }

    /**
     * 注入已构造的 Harness {@link SandboxClient}（通常为 {@link OpenSandboxClient}）。
     *
     * @param client 客户端
     * @return this
     */
    public OpenSandboxFilesystemSpec client(SandboxClient<?> client) {
        this.client = client;
        return this;
    }

    /**
     * 注入会话执行桥（写入 options，供 {@link OpenSandboxClientOptions#createClient()} 使用）。
     *
     * @param sessionRunner 应用层 bridge（包装 SandboxRunner）
     * @return this
     */
    public OpenSandboxFilesystemSpec sessionRunner(OpenSandboxExecBridge sessionRunner) {
        options.execBridge(sessionRunner);
        return this;
    }

    /**
     * @param instanceId 已供给容器 id
     * @return this
     */
    public OpenSandboxFilesystemSpec instanceId(String instanceId) {
        options.instanceId(instanceId);
        return this;
    }

    /**
     * @param sessionNum 平台会话编号
     * @return this
     */
    public OpenSandboxFilesystemSpec sessionNum(String sessionNum) {
        options.sessionNum(sessionNum);
        return this;
    }

    /**
     * @param env 环境变量
     * @return this
     */
    public OpenSandboxFilesystemSpec env(Map<String, String> env) {
        options.env(env);
        return this;
    }

    /**
     * @param ttlMinutes session TTL 分钟
     * @return this
     */
    public OpenSandboxFilesystemSpec ttlMinutes(long ttlMinutes) {
        options.ttlMinutes(ttlMinutes);
        return this;
    }

    /**
     * 兼容短名 {@code ttl}。
     *
     * @param ttlMinutes session TTL 分钟
     * @return this
     */
    public OpenSandboxFilesystemSpec ttl(long ttlMinutes) {
        return ttlMinutes(ttlMinutes);
    }

    /**
     * @param sandboxNum 沙箱资产编号（执行期重建）
     * @return this
     */
    public OpenSandboxFilesystemSpec sandboxNum(String sandboxNum) {
        options.sandboxNum(sandboxNum);
        return this;
    }

    /**
     * @param agentNum Agent 编号（会话卷重建）
     * @return this
     */
    public OpenSandboxFilesystemSpec agentNum(String agentNum) {
        options.agentNum(agentNum);
        return this;
    }

    /**
     * @param workspaceRoot 容器内 workspace 根
     * @return this
     */
    public OpenSandboxFilesystemSpec workspaceRoot(String workspaceRoot) {
        options.workspaceRoot(workspaceRoot);
        return this;
    }

    /**
     * @param snapshotSpec 快照策略
     * @return this
     */
    public OpenSandboxFilesystemSpec snapshotSpec(SandboxSnapshotSpec snapshotSpec) {
        this.snapshotSpec = snapshotSpec != null ? snapshotSpec : new NoopSnapshotSpec();
        return this;
    }

    /**
     * @param workspaceSpec workspace 清单
     * @return this
     */
    public OpenSandboxFilesystemSpec workspaceSpec(WorkspaceSpec workspaceSpec) {
        this.defaultWorkspaceSpec = workspaceSpec != null ? workspaceSpec : new WorkspaceSpec();
        return this;
    }

    @Override
    protected SandboxClient<?> createClient() {
        return client != null ? client : options.createClient();
    }

    @Override
    protected SandboxClientOptions clientOptions() {
        return options;
    }

    @Override
    protected SandboxSnapshotSpec snapshotSpec() {
        SandboxSnapshotSpec override = getSnapshotSpecOverride();
        return override != null ? override : snapshotSpec;
    }

    @Override
    protected WorkspaceSpec workspaceSpec() {
        return defaultWorkspaceSpec;
    }
}
