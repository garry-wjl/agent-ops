package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;

/**
 * 替换 Harness 自带的 {@code execute}：工作目录落在 workspace 根下时先收成相对路径再执行。
 * 命令字符串里的绝对路径原样保留。
 */
public class WorkspaceShellTool {

    private final AbstractSandboxFilesystem sandbox;
    private final String workspaceRoot;

    public WorkspaceShellTool(AbstractSandboxFilesystem sandbox, String workspaceRoot) {
        this.sandbox = sandbox;
        this.workspaceRoot = workspaceRoot;
    }

    @Tool(
            name = "execute",
            description =
                    "Execute a shell command. Use for git, npm, build, test, and other terminal"
                            + " operations. Returns combined output and exit code.")
    public String execute(
            RuntimeContext runtimeContext,
            @ToolParam(name = "command", description = "Shell command to execute") String command,
            @ToolParam(
                            name = "working_directory",
                            description =
                                    "Working directory (relative to workspace root, optional)")
                    String workingDirectory,
            @ToolParam(name = "timeout", description = "Timeout in seconds (default: 30)")
                    int timeout) {
        String relative = WorkspaceRelativePaths.toRelativeOrError(workingDirectory, workspaceRoot);
        if (WorkspaceRelativePaths.REJECTED.equals(relative)) {
            return WorkspaceRelativePaths.REJECTED;
        }
        String effectiveCommand = command;
        if (relative != null) {
            effectiveCommand = "cd '" + relative.replace("'", "'\\''") + "' && " + command;
        }
        ExecuteResponse result =
                sandbox.execute(runtimeContext, effectiveCommand, timeout > 0 ? timeout : 30);
        StringBuilder sb = new StringBuilder();
        sb.append("Exit code: ").append(result.exitCode()).append("\n");
        if (result.output() != null && !result.output().isBlank()) {
            sb.append("\n").append(result.output());
        }
        if (result.truncated()) {
            sb.append("\n(output was truncated)");
        }
        return sb.toString();
    }
}
