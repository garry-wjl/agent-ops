package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxClient;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * OpenSandbox 容器网关（{@code sandbox.provider=opensandbox}，默认）。
 * <p>
 * 会话级 OSS subPath 已下线：{@link #isolatesWorkspaceBySession()} 恒为 false。
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(prefix = "sandbox", name = "provider", havingValue = "opensandbox", matchIfMissing = true)
public class OpenSandboxContainerGateway implements SandboxContainerGateway {

    @Resource
    private SandboxClient sandboxClient;

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes,
                         String workspaceSubPath, Map<String, String> env) {
        // 会话 OSS 隔离已移除：不再按 workspaceSubPath 挂卷
        return sandboxClient.create(cpu, memoryMb, aliveMinutes, env);
    }

    @Override
    public boolean isolatesWorkspaceBySession() {
        return false;
    }

    @Override
    public void kill(String instanceId) {
        sandboxClient.kill(instanceId);
    }

    @Override
    public boolean isAlive(String instanceId) {
        return sandboxClient.isAlive(instanceId);
    }

    @Override
    public void renew(String instanceId, int aliveMinutes) {
        sandboxClient.renew(instanceId, aliveMinutes);
    }
}
