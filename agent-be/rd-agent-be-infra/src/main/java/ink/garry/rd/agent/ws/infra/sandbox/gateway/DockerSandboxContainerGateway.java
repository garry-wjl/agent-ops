package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import ink.garry.rd.agent.ws.infra.sandbox.docker.DockerCli;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本地 Docker 容器网关（替代空 Mock）。
 * <p>
 * 启用：{@code sandbox.mock=true}。生产 {@code mock=false} 走 {@link OpenSandboxContainerGateway}。
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "true")
public class DockerSandboxContainerGateway implements SandboxContainerGateway {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxContainerGateway.class);

    @Resource
    private DockerCli dockerCli;
    @Resource
    private SandboxProperties sandboxProperties;

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String name = "agentops-sbx-" + suffix;
        String image = resolveDockerImage();
        String cpuStr = cpu != null ? cpu.stripTrailingZeros().toPlainString() : null;
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("agentops.sandbox", "true");
        labels.put("agentops.alive-minutes", String.valueOf(Math.max(aliveMinutes, 1)));
        String id = dockerCli.runDetached(name, image, cpuStr, memoryMb, labels);
        log.info("[docker-sandbox] create id={} name={} image={} cpu={} memoryMb={} aliveMinutes={}",
                id.length() > 12 ? id.substring(0, 12) : id, name, image, cpuStr, memoryMb, aliveMinutes);
        return id;
    }

    @Override
    public boolean isolatesWorkspaceBySession() {
        return sandboxProperties.getOss() != null && sandboxProperties.getOss().isEnabled();
    }

    /**
     * 本地没有 OSS 挂载客户端时，用宿主机目录模拟会话前缀，仍然挂到 {@code /workspace}。
     */
    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes, String workspaceSubPath) {
        if (!isolatesWorkspaceBySession() || workspaceSubPath == null || workspaceSubPath.isBlank()) {
            return create(cpu, memoryMb, aliveMinutes);
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String name = "agentops-sbx-" + suffix;
        String image = resolveDockerImage();
        String cpuStr = cpu != null ? cpu.stripTrailingZeros().toPlainString() : null;
        Path host = Path.of(System.getProperty("java.io.tmpdir"), "rd-agent-oss", workspaceSubPath);
        try {
            Files.createDirectories(host);
        } catch (Exception e) {
            throw new IllegalStateException("创建会话工作空间目录失败: " + host, e);
        }
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("agentops.sandbox", "true");
        labels.put("agentops.workspace-sub-path", workspaceSubPath);
        labels.put("agentops.alive-minutes", String.valueOf(Math.max(aliveMinutes, 1)));
        String id = dockerCli.runDetached(name, image, cpuStr, memoryMb, labels, host.toString());
        log.info("[docker-sandbox] session workspace id={} subPath={} host={}",
                id.length() > 12 ? id.substring(0, 12) : id, workspaceSubPath, host);
        return id;
    }

    @Override
    public void kill(String instanceId) {
        dockerCli.removeForce(instanceId);
    }

    @Override
    public boolean isAlive(String instanceId) {
        return dockerCli.isRunning(instanceId);
    }

    private String resolveDockerImage() {
        String dockerImage = sandboxProperties.getDockerImage();
        if (dockerImage != null && !dockerImage.isBlank()) {
            return dockerImage.trim();
        }
        // 回退到统一 image；若指向仅远程可拉的 OpenSandbox 镜像，本地请设 sandbox.docker-image
        String image = sandboxProperties.getImage();
        return image != null && !image.isBlank() ? image.trim() : "ubuntu:22.04";
    }
}
