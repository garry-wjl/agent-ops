package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionWorkspaceMountTest {

    @Test
    void subPath_joinsWorkspaceAgentSession() {
        assertEquals("WS1/AGT1/SES1", SessionWorkspaceMount.subPath("WS1", "AGT1", "SES1"));
        assertEquals("/workspace", SessionWorkspaceMount.MOUNT_PATH);
    }

    @Test
    void subPath_rejectsBlankAndTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionWorkspaceMount.subPath(" ", "AGT1", "SES1"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionWorkspaceMount.subPath("WS1", "../AGT", "SES1"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionWorkspaceMount.subPath("WS1", "AGT/1", "SES1"));
    }
}
