package ink.garry.rd.agent.ws.application.agentrunner.harness.opensandbox;

import ink.garry.rd.agent.ws.application.agentrunner.sandbox.HeaderEnvInjector;
import ink.garry.rd.agent.ws.application.sandbox.lifecycle.SessionSandboxLifecycleService;
import ink.garry.rd.agent.ws.application.sandbox.lifecycle.SessionSandboxSkillProjector;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxCommandResult;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import ink.garry.rd.agent.ws.infra.sandbox.acs.AcsE2bSandboxClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ACS Agent Sandbox 执行桥：exec / 写文件走 E2B envd，会话复用走 {@link SandboxPoolService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(prefix = "sandbox", name = "provider", havingValue = "acs")
public class AcsAgentSandboxExecBridge implements OpenSandboxExecBridge {

    private final AcsE2bSandboxClient acsE2bSandboxClient;
    private final SandboxPoolService sandboxPoolService;
    private final SessionSandboxLifecycleService sessionSandboxLifecycleService;
    private final SessionSandboxSkillProjector sessionSandboxSkillProjector;

    @Override
    public String resolveInstanceId(
            String instanceId, String sessionNum, String sandboxNum, String agentNum) {
        try {
            String live = sessionSandboxLifecycleService.ensureAliveOrRebindForUse(
                    sessionNum, instanceId, sandboxNum, agentNum, "agent-exec");
            sessionSandboxSkillProjector.flush(sessionNum, live);
            return live;
        } catch (Exception e) {
            log.warn("[acs-exec] resolveInstanceId failed sessionNum={}: {}",
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
        String effective = HeaderEnvInjector.prefixExports(env, command);
        try {
            AcsE2bSandboxClient.ExecResult result = acsE2bSandboxClient.exec(instanceId, effective);
            sandboxPoolService.markSessionActive(sessionNum);
            return new OpenSandboxCommandResult(result.exitCode(), result.stdout(), result.stderr());
        } catch (Exception e) {
            return retryOnceAfterRebind(instanceId, sessionNum, env, effective, null, null, e, true);
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
        try {
            acsE2bSandboxClient.writeTextFile(instanceId, absolutePath, utf8Content);
            sandboxPoolService.markSessionActive(sessionNum);
        } catch (Exception e) {
            retryOnceAfterRebind(
                    instanceId, sessionNum, env, null, absolutePath, utf8Content, e, false);
        }
    }

    private OpenSandboxCommandResult retryOnceAfterRebind(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            String command,
            String absolutePath,
            String utf8Content,
            Exception first,
            boolean isExec)
            throws Exception {
        if (!looksLikeDeadContainer(first)) {
            throw first;
        }
        log.warn("[acs-exec] container dead, rebind once sessionNum={} oldId={}: {}",
                sessionNum, instanceId, first.getMessage());
        String live = sessionSandboxLifecycleService.ensureAliveOrRebindForUse(
                sessionNum, instanceId, null, null, "agent-exec-rebind");
        if (live == null || live.equals(instanceId)) {
            throw new IllegalStateException(
                    "沙箱实例已到期且重建失败，请重试。原因: " + first.getMessage(), first);
        }
        if (isExec) {
            AcsE2bSandboxClient.ExecResult result = acsE2bSandboxClient.exec(live, command);
            sandboxPoolService.markSessionActive(sessionNum);
            return new OpenSandboxCommandResult(result.exitCode(), result.stdout(), result.stderr());
        }
        acsE2bSandboxClient.writeTextFile(live, absolutePath, utf8Content);
        sandboxPoolService.markSessionActive(sessionNum);
        return null;
    }

    private static boolean looksLikeDeadContainer(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        Throwable c = e.getCause();
        String cause = c != null && c.getMessage() != null ? c.getMessage().toLowerCase() : "";
        String all = msg + " " + cause;
        return all.contains("not found")
                || all.contains("no such")
                || all.contains("404")
                || all.contains("502")
                || all.contains("connection refused")
                || all.contains("unavailable")
                || all.contains("dead");
    }
}
