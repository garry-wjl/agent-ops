package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import cn.hutool.core.util.StrUtil;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 构造 Harness {@link SandboxContext#getExternalSandbox()}（user-managed）。
 * <p>
 * 注入后 {@code SandboxManager} 每轮 release 不再 {@code shutdown}，避免误清会话工作区；
 * 与 {@link OpenSandboxHarnessSandbox#shutdown()} 跳过平台容器目录销毁形成双保险。
 */
public final class OpenSandboxUserManagedContextFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenSandboxUserManagedContextFactory.class);

    private OpenSandboxUserManagedContextFactory() {
    }

    /**
     * @param execBridge 会话执行桥
     * @param instanceId 已 ensureBound 的容器 id（可空，首次工具执行时懒绑定）
     * @param sessionNum 平台会话号
     * @param sandboxNum 沙箱资产号（instanceId 空时必填）
     * @param agentNum   Agent 号（可空）
     * @param env           环境变量（可空）
     * @param ttlMinutes    session 映射 TTL
     * @param workspaceRoot 容器内 workspace 根（ACS 一般为 {@code /home/user}）
     * @return user-managed {@link SandboxContext}；参数不足时 null
     */
    public static SandboxContext create(
            OpenSandboxExecBridge execBridge,
            String instanceId,
            String sessionNum,
            String sandboxNum,
            String agentNum,
            Map<String, String> env,
            long ttlMinutes,
            String workspaceRoot) {
        if (execBridge == null || StrUtil.isBlank(sessionNum)) {
            return null;
        }
        if (StrUtil.isBlank(instanceId) && StrUtil.isBlank(sandboxNum)) {
            return null;
        }
        String root = StrUtil.blankToDefault(workspaceRoot, "/workspace").trim();
        OpenSandboxClientOptions options = new OpenSandboxClientOptions()
                .execBridge(execBridge)
                .instanceId(StrUtil.isBlank(instanceId) ? null : instanceId.trim())
                .sessionNum(sessionNum.trim())
                .sandboxNum(sandboxNum)
                .agentNum(agentNum)
                .env(env)
                .ttlMinutes(ttlMinutes > 0 ? ttlMinutes : 30L)
                .workspaceRoot(root);
        OpenSandboxClient client = new OpenSandboxClient(execBridge);
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(root);
        NoopSnapshotSpec snapshotSpec = new NoopSnapshotSpec();
        Sandbox sandbox = client.create(workspaceSpec, snapshotSpec, options);
        Objects.requireNonNull(sandbox, "opensandbox harness sandbox");
        log.debug(
                "[sandbox-opensandbox] user-managed context instanceId={} sessionNum={} sandboxNum={} root={}",
                instanceId,
                sessionNum,
                sandboxNum,
                root);
        return SandboxContext.builder()
                .externalSandbox(sandbox)
                .client(client)
                .clientOptions(options)
                .workspaceSpec(workspaceSpec)
                .snapshotSpec(snapshotSpec)
                .isolationScope(IsolationScope.SESSION)
                .build();
    }

    /**
     * 兼容旧调用：默认 workspace 根 {@code /workspace}。
     */
    public static SandboxContext create(
            OpenSandboxExecBridge execBridge,
            String instanceId,
            String sessionNum,
            String sandboxNum,
            String agentNum,
            Map<String, String> env,
            long ttlMinutes) {
        return create(execBridge, instanceId, sessionNum, sandboxNum, agentNum, env, ttlMinutes, "/workspace");
    }
}
