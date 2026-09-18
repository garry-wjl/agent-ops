package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

/**
 * OpenSandbox 会话内命令执行结果（Harness {@code ExecResult} 的桥接载体）。
 * <p>
 * 由应用层把 OpenSandbox {@code Execution}（优先读 logs.stdout/stderr）映射为本类型，
 * 避免 infra 的 Harness SPI 直接依赖 application 的 {@code SandboxRunner}。
 *
 * @param exitCode 进程退出码；{@code null} 时按 {@code -1} 处理
 * @param stdout   标准输出（可空）
 * @param stderr   标准错误（可空）
 */
public record OpenSandboxCommandResult(Integer exitCode, String stdout, String stderr) {

    /**
     * 归一化退出码：{@code null} → {@code -1}。
     *
     * @return 非空退出码
     */
    public int normalizedExitCode() {
        return exitCode == null ? -1 : exitCode;
    }

    /**
     * 安全取 stdout。
     *
     * @return 非 null 字符串
     */
    public String safeStdout() {
        return stdout == null ? "" : stdout;
    }

    /**
     * 安全取 stderr。
     *
     * @return 非 null 字符串
     */
    public String safeStderr() {
        return stderr == null ? "" : stderr;
    }
}
