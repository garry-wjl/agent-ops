package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MockSandboxContainerGateway} 行为测试（不连真实 OpenSandbox）。
 */
class MockSandboxContainerGatewayTest {

    private MockSandboxContainerGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockSandboxContainerGateway();
        gateway.reset();
    }

    @Test
    void createKillIsAlive() {
        String id = gateway.create(BigDecimal.ONE, 1024, 10);
        assertTrue(gateway.isAlive(id));
        assertEquals(1, gateway.size());
        gateway.kill(id);
        assertFalse(gateway.isAlive(id));
        assertEquals(0, gateway.size());
    }
}
