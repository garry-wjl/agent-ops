package ink.garry.rd.agent.ws.application.agentrunner.harness.opensandbox;

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

    @Override
    public OpenSandboxCommandResult exec(
            String instanceId,
            String sessionNum,
            Map<String, String> env,
            long ttlMinutes,
            String command) {
        log.debug("[docker-opensandbox-exec] exec instanceId={} sessionNum={} cmd={}",
                shortId(instanceId), sessionNum, command);
        DockerCli.ExecResult r = dockerCli.exec(instanceId, env, null, command);
        return new OpenSandboxCommandResult(r.exitCode(), r.stdout(), r.stderr());
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
        dockerCli.writeTextFile(instanceId, absolutePath, utf8Content);
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 12 ? id : id.substring(0, 12);
    }
}
