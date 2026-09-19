package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionLogs;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.OutputMessage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把 OpenSandbox {@link Execution} 映射为 {@link OpenSandboxCommandResult}。
 * <p>
 * 与 {@code SandboxTool#renderExecution} 同口径：shell / python 输出落在 {@code logs.stdout/stderr}，
 * 不能只读 Jupyter 式 {@code result}。
 */
public final class OpenSandboxExecutionMapper {

    private OpenSandboxExecutionMapper() {}

    /**
     * @param execution OpenSandbox 执行结果（可空 → 退出码 -1）
     * @return 桥接结果
     */
    public static OpenSandboxCommandResult from(Execution execution) {
        if (execution == null) {
            return new OpenSandboxCommandResult(-1, "", "null execution");
        }
        String stdout = "";
        String stderr = "";
        ExecutionLogs logs = execution.getLogs();
        if (logs != null) {
            stdout = joinMessages(logs.getStdout());
            stderr = joinMessages(logs.getStderr());
        }
        if (execution.getError() != null) {
            String errLine =
                    execution.getError().getName() + ": " + execution.getError().getValue();
            stderr = stderr.isEmpty() ? errLine : stderr + "\n" + errLine;
        }
        return new OpenSandboxCommandResult(execution.getExitCode(), stdout, stderr);
    }

    private static String joinMessages(List<OutputMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream().map(OutputMessage::getText).collect(Collectors.joining());
    }
}
