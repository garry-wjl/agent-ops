package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.infra.sandbox.acs.AcsE2bSandboxClient;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ACS Agent Sandbox 容器网关（{@code sandbox.provider=acs}）。
 * <p>
 * 单独 Agent 不传目录前缀，创建请求与原来一致。编排运行传入前缀时，按官方 E2B metadata 挂同一块 OSS 子目录。
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(prefix = "sandbox", name = "provider", havingValue = "acs")
public class AcsAgentSandboxContainerGateway implements SandboxContainerGateway {

    @Resource
    private AcsE2bSandboxClient acsE2bSandboxClient;

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes,
                         String workspaceSubPath, Map<String, String> env) {
        return acsE2bSandboxClient.create(cpu, memoryMb, aliveMinutes, env, workspaceSubPath);
    }

    @Override
    public boolean isolatesWorkspaceBySession() {
        return false;
    }

    @Override
    public void kill(String instanceId) {
        acsE2bSandboxClient.kill(instanceId);
    }

    @Override
    public boolean isAlive(String instanceId) {
        return acsE2bSandboxClient.isAlive(instanceId);
    }

    @Override
    public void renew(String instanceId, int aliveMinutes) {
        acsE2bSandboxClient.renew(instanceId, aliveMinutes);
    }
}
