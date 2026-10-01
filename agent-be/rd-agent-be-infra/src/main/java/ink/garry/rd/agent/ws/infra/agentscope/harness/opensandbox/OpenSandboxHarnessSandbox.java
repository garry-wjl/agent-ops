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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenSandbox 实现的 Harness {@link io.agentscope.harness.agent.sandbox.Sandbox}。
 * <p>
 * 命令经 {@link OpenSandboxExecBridge} 走到平台会话复用（应用层通常包装 {@code SandboxRunner#obtainSession}
 * + {@code runInSession}）。workspace 根固定为状态中的路径（默认 {@code /workspace}）；
 * persist/hydrate 用 tar+base64 over exec（首期默认 {@code NoopSnapshotSpec} 时 persist 不会被 stop 触发）。
 * <p>
 * <b>懒启动</b>：Harness 每轮 {@code stream} 都会 {@code acquire → start()}。本实现的
 * {@link #start()} <strong>不</strong>触发 {@code ensureBound}/远端 mkdir，把绑定推迟到首次
 * {@link #doExec} / 写文件，避免纯对话被沙箱冷启动阻塞。
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
     * 本地「已 start」标记（不代表远端容器已 ensure）。
     * 父类 {@code running} 为 private，故用本字段承接懒启动语义。
     */
    private final AtomicBoolean locallyStarted = new AtomicBoolean(false);

    /** 远端 workspace 根是否已在本句柄上 mkdir 成功。 */
    private final AtomicBoolean remoteWorkspacePrepared = new AtomicBoolean(false);

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
     * Harness 每轮 stream 入口会调用；此处故意不做远端 ensure/probe/mkdir。
     * <p>
     * 真实绑定与 workspace 初始化见 {@link #ensureRemoteWorkspacePrepared()}（首次 exec 时）。
     */
    @Override
    public void start() {
        locallyStarted.set(true);
        log.debug(
                "[sandbox-opensandbox] lazy start (defer ensure/mkdir) sessionNum={} instanceId={}",
                openState.getSessionNum(),
                openState.getInstanceId());
    }

    /**
     * 与懒 {@link #start()} 配对：不触发远端 persist/ensure（Noop 快照下本就无持久化）。
     */
    @Override
    public void stop() {
        openState.setWorkspaceRootReady(true);
        locallyStarted.set(false);
    }

    @Override
    public boolean isRunning() {
        return locallyStarted.get();
    }

    /**
     * Harness 每轮 release 可能调用本方法。
     * <p>
     * 平台供给容器（{@code containerOwned=false}）时<strong>不</strong>清理 {@code /workspace}：
     * 会话级文件系统应跨多轮 Agent 调用保留；目录随平台 {@code kill}/回收销毁。
     * 仅在 SPI 自持容器（{@code containerOwned=true}）时尝试 {@link #doDestroyWorkspace()}。
     *
     * @throws Exception 自持容器清理失败时上抛（目录清理失败仅打日志）
     */
    @Override
    public void shutdown() throws Exception {
        if (!openState.isContainerOwned()) {
            log.debug(
                    "[sandbox-opensandbox] skip workspace destroy (platform-owned session FS) instanceId={}",
                    openState.getInstanceId());
            return;
        }
        log.warn(
                "[sandbox-opensandbox] containerOwned=true but kill is not implemented; "
                        + "destroy workspace only. instanceId={}",
                openState.getInstanceId());
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
     * <p>首次调用会 {@code resolveInstanceId}（可能 ensureBound）并 mkdir workspace。
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
        String instanceId = ensureRemoteWorkspacePrepared();
        try {
            OpenSandboxCommandResult raw =
                    execBridge.exec(
                            instanceId,
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
        } catch (Exception e) {
            throw wrapDeadContainer(e);
        }
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
        String instanceId = ensureRemoteWorkspacePrepared();
        OpenSandboxCommandResult raw =
                execBridge.exec(
                        instanceId,
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
        String instanceId = ensureRemoteWorkspacePrepared();
        if (tarBytes.length == 0) {
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(tarBytes);
        String tmpPath =
                "/tmp/as_ws_hydrate_" + UUID.randomUUID().toString().replace("-", "") + ".b64";
        execBridge.writeTextFile(
                instanceId,
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
                        instanceId,
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
     * {@code mkdir -p} workspace 根（经 {@link #ensureRemoteWorkspacePrepared()}，会懒 ensure）。
     */
    @Override
    protected void doSetupWorkspace() throws Exception {
        ensureRemoteWorkspacePrepared();
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
        String instanceId = resolveLiveInstanceId();
        execBridge.exec(
                instanceId,
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

    /**
     * 首次真实使用时：resolve/ensure 实例 + mkdir workspace；之后同句柄复用。
     *
     * @return 可用 instanceId
     */
    private String ensureRemoteWorkspacePrepared() throws Exception {
        String existingId = openState.getInstanceId();
        if (remoteWorkspacePrepared.get() && existingId != null && !existingId.isBlank()) {
            return resolveLiveInstanceId();
        }
        synchronized (this) {
            String instanceId = resolveLiveInstanceId();
            if (remoteWorkspacePrepared.get()) {
                return instanceId;
            }
            String root = getWorkspaceRoot();
            OpenSandboxCommandResult raw =
                    execBridge.exec(
                            instanceId,
                            openState.getSessionNum(),
                            openState.getEnv(),
                            openState.getTtlMinutes(),
                            "mkdir -p " + shellSingleQuote(root));
            if (raw.normalizedExitCode() != 0) {
                throw new SandboxException.SandboxRuntimeException(
                        SandboxErrorCode.WORKSPACE_START_ERROR,
                        "mkdir workspace failed: " + raw.safeStderr());
            }
            openState.setWorkspaceRootReady(true);
            remoteWorkspacePrepared.set(true);
            log.debug(
                    "[sandbox-opensandbox] remote workspace prepared sessionNum={} instanceId={}",
                    openState.getSessionNum(),
                    instanceId);
            return instanceId;
        }
    }

    /**
     * 执行前解析可用实例，若重建则回写状态。
     *
     * @return 可用 instanceId
     */
    private String resolveLiveInstanceId() {
        String live =
                execBridge.resolveInstanceId(
                        openState.getInstanceId(),
                        openState.getSessionNum(),
                        openState.getSandboxNum(),
                        openState.getAgentNum());
        if (live != null && !live.equals(openState.getInstanceId())) {
            log.warn(
                    "[sandbox-opensandbox] instance rebound sessionNum={} old={} new={}",
                    openState.getSessionNum(),
                    openState.getInstanceId(),
                    live);
            openState.setInstanceId(live);
        }
        return live != null ? live : openState.getInstanceId();
    }

    private Exception wrapDeadContainer(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (looksLikeDeadContainer(msg)) {
            return new SandboxException.SandboxRuntimeException(
                    SandboxErrorCode.CONFIGURATION_ERROR,
                    "沙箱实例已到期或不可用，已尝试重建；若仍失败请重试本步"
                            + "（会话工作区在 PVC 上可保留）。原因: "
                            + msg,
                    e);
        }
        return e;
    }

    private static boolean looksLikeDeadContainer(String msg) {
        String m = msg.toLowerCase();
        return m.contains("not found")
                || m.contains("no such")
                || m.contains("dead")
                || m.contains("502")
                || m.contains("connection refused")
                || m.contains("unavailable");
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
