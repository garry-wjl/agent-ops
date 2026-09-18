package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.AbstractBaseSandbox;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.SandboxErrorCode;
import io.agentscope.harness.agent.sandbox.SandboxException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenSandbox 实现的 Harness {@link io.agentscope.harness.agent.sandbox.Sandbox}。
 * <p>
 * 命令经 {@link OpenSandboxExecBridge} 走到平台会话复用（应用层通常包装 {@code SandboxRunner#obtainSession}
 * + {@code runInSession}）。workspace 根固定为状态中的路径（默认 {@code /workspace}）；
 * persist/hydrate 用 tar+base64 over exec（首期默认 {@code NoopSnapshotSpec} 时 persist 不会被 stop 触发，
 * 但 start 投影仍可能调用 hydrate）。
 */
public class OpenSandboxHarnessSandbox extends AbstractBaseSandbox {

    private static final Logger log = LoggerFactory.getLogger(OpenSandboxHarnessSandbox.class);

    /** 单流输出截断上限（字节，按 UTF-8 字符长度近似）。 */
    private static final int OUTPUT_TRUNCATE_CHARS = 512 * 1024;

    /** 状态。 */
    private final OpenSandboxSandboxState openState;

    /** 会话执行桥。 */
    private final OpenSandboxExecBridge execBridge;

    /**
     * @param state      OpenSandbox 状态
     * @param execBridge 执行桥
     */
    public OpenSandboxHarnessSandbox(OpenSandboxSandboxState state, OpenSandboxExecBridge execBridge) {
        super(state);
        this.openState = Objects.requireNonNull(state, "state");
        this.execBridge = Objects.requireNonNull(execBridge, "execBridge");
    }

    /**
     * 不销毁平台容器；仅尝试清理 workspace 目录。
     *
     * @throws Exception 清理失败时上抛（目录清理失败仅打日志）
     */
    @Override
    public void shutdown() throws Exception {
        if (openState.isContainerOwned()) {
            log.warn(
                    "[sandbox-opensandbox] containerOwned=true but kill is not implemented; "
                            + "rely on platform lifecycle. instanceId={}",
                    openState.getInstanceId());
        }
        try {
            doDestroyWorkspace();
        } catch (Exception e) {
            log.warn(
                    "[sandbox-opensandbox] destroy workspace failed instanceId={}: {}",
                    openState.getInstanceId(),
                    e.getMessage());
        }
    }

    /**
     * 在会话 bash 中执行命令；cwd 切到 workspace 根。
     * <p>非 0 退出码仍返回 {@link ExecResult}（供 probe / 文件工具语义），不抛异常；
     * 连接失败等基础设施错误才上抛。
     *
     * @param runtimeContext 可空（内部 probe 为 null）
     * @param command        shell 命令
     * @param timeoutSeconds 超时秒（桥接层若未透传则由远端默认）
     * @return 执行结果
     */
    @Override
    protected ExecResult doExec(RuntimeContext runtimeContext, String command, int timeoutSeconds)
            throws Exception {
        String wrapped = wrapWithWorkspaceCd(command);
        OpenSandboxCommandResult raw =
                execBridge.exec(
                        openState.getInstanceId(),
                        openState.getSessionNum(),
                        openState.getEnv(),
                        openState.getTtlMinutes(),
                        wrapped);
        String stdout = truncate(raw.safeStdout());
        String stderr = truncate(raw.safeStderr());
        boolean truncated =
                raw.safeStdout().length() > OUTPUT_TRUNCATE_CHARS
                        || raw.safeStderr().length() > OUTPUT_TRUNCATE_CHARS;
        return new ExecResult(raw.normalizedExitCode(), stdout, stderr, truncated);
    }

    /**
     * 通过 {@code tar | base64} 导出 workspace 归档。
     *
     * @return tar 字节流
     */
    @Override
    protected InputStream doPersistWorkspace() throws Exception {
        String root = getWorkspaceRoot();
        // -w0：单行 base64，避免换行干扰解码
        String cmd =
                "tar -cf - -C "
                        + shellSingleQuote(root)
                        + " . 2>/dev/null | base64 -w0 2>/dev/null || tar -cf - -C "
                        + shellSingleQuote(root)
                        + " . 2>/dev/null | base64";
        OpenSandboxCommandResult raw =
                execBridge.exec(
                        openState.getInstanceId(),
                        openState.getSessionNum(),
                        openState.getEnv(),
                        openState.getTtlMinutes(),
                        cmd);
        if (raw.normalizedExitCode() != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                    "opensandbox tar|base64 failed (exit="
                            + raw.normalizedExitCode()
                            + "): "
                            + raw.safeStderr());
        }
        String b64 = raw.safeStdout().replaceAll("\\s+", "");
        if (b64.isEmpty()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        try {
            return new ByteArrayInputStream(Base64.getDecoder().decode(b64));
        } catch (IllegalArgumentException e) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_WRITE_ERROR,
                    "invalid base64 from opensandbox tar export",
                    e);
        }
    }

    /**
     * 将 tar 归档经 base64 中间文件写回 workspace。
     *
     * @param archive tar 输入流
     */
    @Override
    protected void doHydrateWorkspace(InputStream archive) throws Exception {
        Objects.requireNonNull(archive, "archive");
        byte[] tarBytes = archive.readAllBytes();
        String root = getWorkspaceRoot();
        doSetupWorkspace();
        if (tarBytes.length == 0) {
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(tarBytes);
        String tmpPath =
                "/tmp/as_ws_hydrate_" + UUID.randomUUID().toString().replace("-", "") + ".b64";
        execBridge.writeTextFile(
                openState.getInstanceId(),
                openState.getSessionNum(),
                openState.getEnv(),
                openState.getTtlMinutes(),
                tmpPath,
                b64);
        String cmd =
                "base64 -d "
                        + shellSingleQuote(tmpPath)
                        + " 2>/dev/null | tar -xf - -C "
                        + shellSingleQuote(root)
                        + " ; rm -f "
                        + shellSingleQuote(tmpPath);
        OpenSandboxCommandResult raw =
                execBridge.exec(
                        openState.getInstanceId(),
                        openState.getSessionNum(),
                        openState.getEnv(),
                        openState.getTtlMinutes(),
                        cmd);
        if (raw.normalizedExitCode() != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_ARCHIVE_READ_ERROR,
                    "opensandbox base64|tar hydrate failed (exit="
                            + raw.normalizedExitCode()
                            + "): "
                            + raw.safeStderr());
        }
    }

    /**
     * {@code mkdir -p} workspace 根。
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        String root = getWorkspaceRoot();
        OpenSandboxCommandResult raw =
                execBridge.exec(
                        openState.getInstanceId(),
                        openState.getSessionNum(),
                        openState.getEnv(),
                        openState.getTtlMinutes(),
                        "mkdir -p " + shellSingleQuote(root));
        if (raw.normalizedExitCode() != 0) {
            throw new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.WORKSPACE_START_ERROR,
                    "mkdir workspace failed: " + raw.safeStderr());
        }
    }

    /**
     * 尽力删除 workspace 根（失败仅日志，由 {@link #shutdown()} 吞掉）。
     */
    @Override
    protected void doDestroyWorkspace() throws Exception {
        String root = getWorkspaceRoot();
        if (root == null || root.isBlank() || "/".equals(root.trim())) {
            return;
        }
        execBridge.exec(
                openState.getInstanceId(),
                openState.getSessionNum(),
                openState.getEnv(),
                openState.getTtlMinutes(),
                "rm -rf " + shellSingleQuote(root));
    }

    @Override
    protected String getWorkspaceRoot() {
        String root = openState.getWorkspaceRoot();
        return root != null && !root.isBlank() ? root : "/workspace";
    }

    /**
     * 供单测 / 诊断读取状态。
     *
     * @return OpenSandbox 状态
     */
    public OpenSandboxSandboxState getOpenSandboxState() {
        return openState;
    }

    private String wrapWithWorkspaceCd(String command) {
        String root = getWorkspaceRoot();
        return "cd " + shellSingleQuote(root) + " && " + command;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= OUTPUT_TRUNCATE_CHARS) {
            return s;
        }
        return s.substring(0, OUTPUT_TRUNCATE_CHARS);
    }

    /**
     * 单引号包裹，内部单引号按 shell 惯例转义。
     *
     * @param value 原始路径/片段
     * @return 可嵌入 sh -c 的字面量
     */
    static String shellSingleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 暴露给单测：直接映射 bridge 结果（含非 0 抛异常语义）。
     *
     * @param raw bridge 原始结果
     * @return ExecResult
     */
    static ExecResult toExecResultOrThrow(OpenSandboxCommandResult raw) {
        String stdout = truncate(raw.safeStdout());
        String stderr = truncate(raw.safeStderr());
        boolean truncated =
                raw.safeStdout().length() > OUTPUT_TRUNCATE_CHARS
                        || raw.safeStderr().length() > OUTPUT_TRUNCATE_CHARS;
        ExecResult result = new ExecResult(raw.normalizedExitCode(), stdout, stderr, truncated);
        if (!result.ok()) {
            throw new SandboxException.ExecException(result.exitCode(), stdout, stderr);
        }
        return result;
    }
}
