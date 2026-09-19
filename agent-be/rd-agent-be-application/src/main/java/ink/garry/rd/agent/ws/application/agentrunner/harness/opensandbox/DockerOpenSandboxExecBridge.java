package ink.garry.rd.agent.ws.application.agentrunner.harness.opensandbox;

import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxCommandResult;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import ink.garry.rd.agent.ws.infra.sandbox.docker.DockerCli;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code sandbox.mock=true}：用本机 Docker 执行 Harness 沙箱命令（真实 shell，非空 Mock）。
 * <p>
 * 与 {@link SandboxRunnerOpenSandboxExecBridge}（OpenSandbox）互斥。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "true")
public class DockerOpenSandboxExecBridge implements OpenSandboxExecBridge {

    private final DockerCli dockerCli;
    private final SandboxPoolService sandboxPoolService;

    @Override
    public String resolveInstanceId(
            String instanceId, String sessionNum, String sandboxNum, String agentNum) {
        try {
            return sandboxPoolService.ensureAliveOrRebind(
                    sessionNum, instanceId, sandboxNum, agentNum, "docker-exec");
        } catch (Exception e) {
            log.warn("[docker-opensandbox-exec] resolveInstanceId failed sessionNum={}: {}",
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
            String command) {
        log.debug("[docker-opensandbox-exec] exec instanceId={} sessionNum={} cmd={}",
                shortId(instanceId), sessionNum, command);
        try {
            DockerCli.ExecResult r = dockerCli.exec(instanceId, env, null, command);
            sandboxPoolService.markSessionActive(sessionNum);
            return new OpenSandboxCommandResult(r.exitCode(), r.stdout(), r.stderr());
        } catch (RuntimeException e) {
            String live = tryRebind(sessionNum, instanceId, sandboxNumFromNull(), agentNumFromNull());
            if (live == null || live.equals(instanceId)) {
                throw e;
            }
            log.warn("[docker-opensandbox-exec] rebound after exec failure old={} new={}",
                    shortId(instanceId), shortId(live));
            throw new IllegalStateException(
                    "沙箱实例已到期并重建，请重试本步（工作区文件在会话卷上可保留）。新实例="
                            + shortId(live),
                    e);
        }
    }

    @Override
    public void writeTextFile(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String absolutePath,
            String utf8Content) {
        log.debug("[docker-opensandbox-exec] writeFile instanceId={} path={} bytes={}",
                shortId(instanceId), absolutePath,
                utf8Content == null ? 0 : utf8Content.length());
        try {
            dockerCli.writeTextFile(instanceId, absolutePath, utf8Content);
            sandboxPoolService.markSessionActive(sessionNum);
        } catch (RuntimeException e) {
            String live = tryRebind(sessionNum, instanceId, null, null);
            if (live == null || live.equals(instanceId)) {
                throw e;
            }
            throw new IllegalStateException(
                    "沙箱实例已到期并重建，请重试本步。新实例=" + shortId(live), e);
        }
    }

    private String tryRebind(String sessionNum, String instanceId, String sandboxNum, String agentNum) {
        try {
            return sandboxPoolService.ensureAliveOrRebind(
                    sessionNum, instanceId, sandboxNum, agentNum, "docker-exec-rebind");
        } catch (Exception ex) {
            log.warn("[docker-opensandbox-exec] rebind failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String sandboxNumFromNull() {
        return null;
    }

    private static String agentNumFromNull() {
        return null;
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 12 ? id : id.substring(0, 12);
    }
}
