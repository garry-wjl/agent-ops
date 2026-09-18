package ink.garry.rd.agent.ws.application.agentrunner.harness.opensandbox;

import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionError;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionLogs;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.OutputMessage;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.RunInSessionRequest;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxSession;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxCommandResult;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 {@link SandboxRunner} 适配为 Harness OpenSandbox 执行桥。
 */
@Component
@RequiredArgsConstructor
public class SandboxRunnerOpenSandboxExecBridge implements OpenSandboxExecBridge {

    private final SandboxRunner sandboxRunner;

    @Override
    public OpenSandboxCommandResult exec(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String command)
            throws Exception {
        try (SandboxSession session =
                     sandboxRunner.obtainSession(instanceId, sessionNum, env, ttlMinutes)) {
            Execution execution = session.sandbox().commands()
                    .runInSession(session.execdSessionId(),
                            RunInSessionRequest.builder().command(command).build());
            return toResult(execution);
        }
    }

    @Override
    public void writeTextFile(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String absolutePath,
            String utf8Content)
            throws Exception {
        try (SandboxSession session =
                     sandboxRunner.obtainSession(instanceId, sessionNum, env, ttlMinutes)) {
            session.sandbox().files().writeFile(absolutePath, utf8Content);
        }
    }

    private static OpenSandboxCommandResult toResult(Execution execution) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        ExecutionLogs logs = execution.getLogs();
        if (logs != null) {
            stdout.append(joinMessages(logs.getStdout()));
            stderr.append(joinMessages(logs.getStderr()));
        }
        ExecutionError error = execution.getError();
        if (error != null) {
            if (!stderr.isEmpty()) {
                stderr.append('\n');
            }
            stderr.append(error.getName() != null ? error.getName() : "error")
                    .append(": ")
                    .append(error.getValue() != null ? error.getValue() : "");
        }
        Integer exit = execution.getExitCode();
        return new OpenSandboxCommandResult(exit, stdout.toString(), stderr.toString());
    }

    private static String joinMessages(List<OutputMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(OutputMessage::getText)
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.joining());
    }
}
