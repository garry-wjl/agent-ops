package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock OpenSandbox：内存登记实例，供本地与自动化测试不连真实网关。
 * <p>
 * 启用：{@code sandbox.mock=true}
 */
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "true")
public class MockSandboxContainerGateway implements SandboxContainerGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSandboxContainerGateway.class);

    private final Map<String, Boolean> alive = new ConcurrentHashMap<>();

    @Override
    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes) {
        String id = "mock-os-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        alive.put(id, Boolean.TRUE);
        log.info("[mock-sandbox] create id={}, cpu={}, memoryMb={}, aliveMinutes={}",
                id, cpu, memoryMb, aliveMinutes);
        return id;
    }

    @Override
    public void kill(String instanceId) {
        alive.remove(instanceId);
        log.info("[mock-sandbox] kill id={}", instanceId);
    }

    @Override
    public boolean isAlive(String instanceId) {
        return Boolean.TRUE.equals(alive.get(instanceId));
    }

    /**
     * 测试辅助：清空内存态。
     */
    public void reset() {
        alive.clear();
    }

    /**
     * 测试辅助：当前存活数。
     *
     * @return 数量
     */
    public int size() {
        return alive.size();
    }
}
