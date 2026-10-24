package ink.garry.rd.agent.ws.application.agentrunner.factory;

import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentRunnerFactory#isSandboxEnabled}：enableSandbox 优先，旧 sandboxRef 兜底。
 */
class AgentRunnerFactorySandboxEnabledTest {

    @Test
    void nullSnapshot_disabled() {
        assertFalse(AgentRunnerFactory.isSandboxEnabled(null));
    }

    @Test
    void enableSandboxTrue_wins() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .enableSandbox(true)
                .sandboxRef(null)
                .build();
        assertTrue(AgentRunnerFactory.isSandboxEnabled(snap));
    }

    @Test
    void enableSandboxFalse_ignoresLegacyRef() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .enableSandbox(false)
                .sandboxRef("SBX-legacy")
                .build();
        assertFalse(AgentRunnerFactory.isSandboxEnabled(snap));
    }

    @Test
    void legacySandboxRef_enablesWhenFlagNull() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder()
                .sandboxRef("SBX-1")
                .build();
        assertTrue(AgentRunnerFactory.isSandboxEnabled(snap));
    }

    @Test
    void neitherFlagNorRef_disabled() {
        AgentDTO.ConfigSnapshot snap = AgentDTO.ConfigSnapshot.builder().build();
        assertFalse(AgentRunnerFactory.isSandboxEnabled(snap));
    }
}
