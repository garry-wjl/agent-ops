package ink.garry.rd.agent.ws.application.agentrunner.harness.opensandbox;

import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionError;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.ExecutionLogs;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.OutputMessage;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.RunInSessionRequest;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxSession;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxCommandResult;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 {@link SandboxRunner} 适配为 Harness OpenSandbox 执行桥（真实网关）。
 * <p>
 * {@code sandbox.mock=true} 时改用 {@link DockerOpenSandboxExecBridge}（本机 Docker）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
public class SandboxRunnerOpenSandboxExecBridge implements OpenSandboxExecBridge {

    private final SandboxRunner sandboxRunner;
    private final SandboxPoolService sandboxPoolService;

    @Override
    public String resolveInstanceId(
            String instanceId, String sessionNum, String sandboxNum, String agentNum) {
        try {
            return sandboxPoolService.ensureAliveOrRebind(
                    sessionNum, instanceId, sandboxNum, agentNum, "agent-exec");
        } catch (Exception e) {
            log.warn("[opensandbox-exec] resolveInstanceId failed sessionNum={}: {}",
                    sessionNum, e.getMessage());
            return instanceId;
        }
    }

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
            sandboxPoolService.markSessionActive(sessionNum);
            return toResult(execution);
        } catch (Exception e) {
            return retryOnceAfterRebind(instanceId, sessionNum, env, ttlMinutes, command, null, null, e, true);
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
            sandboxPoolService.markSessionActive(sessionNum);
        } catch (Exception e) {
            retryOnceAfterRebind(
                    instanceId, sessionNum, env, ttlMinutes, null, absolutePath, utf8Content, e, false);
        }
    }

    private OpenSandboxCommandResult retryOnceAfterRebind(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String command,
            String absolutePath,
            String utf8Content,
            Exception first,
            boolean isExec)
            throws Exception {
        if (!looksLikeDeadContainer(first)) {
            throw first;
        }
        log.warn("[opensandbox-exec] container dead, rebind once sessionNum={} oldId={}: {}",
                sessionNum, instanceId, first.getMessage());
        // 强制走 ensureBound：先清死绑定
        String live = sandboxPoolService.ensureAliveOrRebind(
                sessionNum, instanceId, null, null, "agent-exec-rebind");
        if (live == null || live.equals(instanceId)) {
            throw new IllegalStateException(
                    "沙箱实例已到期且重建失败，请重试。原因: " + first.getMessage(), first);
        }
        if (isExec) {
            try (SandboxSession session =
                         sandboxRunner.obtainSession(live, sessionNum, env, ttlMinutes)) {
                Execution execution = session.sandbox().commands()
                        .runInSession(session.execdSessionId(),
                                RunInSessionRequest.builder().command(command).build());
                sandboxPoolService.markSessionActive(sessionNum);
                return toResult(execution);
            }
        }
        try (SandboxSession session =
                     sandboxRunner.obtainSession(live, sessionNum, env, ttlMinutes)) {
            session.sandbox().files().writeFile(absolutePath, utf8Content);
            sandboxPoolService.markSessionActive(sessionNum);
        }
        return null;
    }

    private static boolean looksLikeDeadContainer(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        Throwable c = e.getCause();
        String cause = c != null && c.getMessage() != null ? c.getMessage().toLowerCase() : "";
        String all = msg + " " + cause;
        return all.contains("not found")
                || all.contains("no such")
                || all.contains("502")
                || all.contains("connection refused")
                || all.contains("unavailable")
                || all.contains("dead");
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
