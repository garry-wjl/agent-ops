package ink.garry.rd.agent.ws.application.agentrunner.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkspaceRelativePathsTest {

    @Test
    void stripsAcsAndWorkspaceRoots() {
        assertEquals("skills/demo", WorkspaceRelativePaths.toRelativeOrError("/home/user/skills/demo", "/home/user"));
        assertEquals("skills/demo", WorkspaceRelativePaths.toRelativeOrError("/workspace/skills/demo", "/home/user"));
        assertEquals("skills/demo", WorkspaceRelativePaths.toRelativeOrError("/home/user/skills/demo/", "/workspace"));
        assertNull(WorkspaceRelativePaths.toRelativeOrError("/home/user", "/home/user"));
        assertNull(WorkspaceRelativePaths.toRelativeOrError("  ", "/home/user"));
    }

    @Test
    void keepsRelativeAndRejectsEscape() {
        assertEquals("skills/demo", WorkspaceRelativePaths.toRelativeOrError("skills/demo", "/home/user"));
        assertEquals(WorkspaceRelativePaths.REJECTED, WorkspaceRelativePaths.toRelativeOrError("/etc", "/home/user"));
        assertEquals(WorkspaceRelativePaths.REJECTED, WorkspaceRelativePaths.toRelativeOrError("~", "/home/user"));
        assertEquals(WorkspaceRelativePaths.REJECTED, WorkspaceRelativePaths.toRelativeOrError("skills/../etc", "/home/user"));
    }
}
