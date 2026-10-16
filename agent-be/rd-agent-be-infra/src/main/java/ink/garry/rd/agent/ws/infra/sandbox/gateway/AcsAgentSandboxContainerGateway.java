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
 * 会话级 OSS subPath 永久关闭：{@link #isolatesWorkspaceBySession()} 恒为 false。
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
        // workspaceSubPath 忽略：会话 OSS 隔离已下线
        return acsE2bSandboxClient.create(cpu, memoryMb, aliveMinutes, env);
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
