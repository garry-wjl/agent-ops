package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxClient;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 真实 OpenSandbox 容器网关（默认启用）。
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
public class OpenSandboxContainerGateway implements SandboxContainerGateway {

    @Resource
    private SandboxClient sandboxClient;

    @Resource
    private SandboxProperties sandboxProperties;

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes) {
        return sandboxClient.create(cpu, memoryMb, aliveMinutes);
    }

    @Override
    public boolean isolatesWorkspaceBySession() {
        return sandboxProperties.getOss() != null && sandboxProperties.getOss().isEnabled();
    }

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes, String workspaceSubPath) {
        return sandboxClient.create(cpu, memoryMb, aliveMinutes, workspaceSubPath);
    }

    @Override
    public void kill(String instanceId) {
        sandboxClient.kill(instanceId);
    }

    @Override
    public boolean isAlive(String instanceId) {
        return sandboxClient.isAlive(instanceId);
    }
}
