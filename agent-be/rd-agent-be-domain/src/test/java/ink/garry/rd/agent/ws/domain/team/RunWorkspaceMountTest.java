package ink.garry.rd.agent.ws.domain.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunWorkspaceMountTest {

    @Test
    void subPath_usesWorkspaceAndRun() {
        assertEquals("WS1/runs/ATR1", RunWorkspaceMount.subPath("WS1", "ATR1"));
    }

    @Test
    void subPath_rejectsTraversal() {
        assertThrows(IllegalArgumentException.class, () -> RunWorkspaceMount.subPath("WS1", "../ATR"));
        assertThrows(IllegalArgumentException.class, () -> RunWorkspaceMount.subPath(" ", "ATR1"));
    }
}
