package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceShellToolTest {

    @Test
    void rewritesAbsoluteWorkingDirectoryAndKeepsCommand() {
        AbstractSandboxFilesystem sandbox = mock(AbstractSandboxFilesystem.class);
        when(sandbox.execute(any(), any(), any())).thenReturn(new ExecuteResponse("SKILL_OK\n", 0, false));
        WorkspaceShellTool tool = new WorkspaceShellTool(sandbox, "/home/user");

        String out = tool.execute(
                null,
                "python3 /home/user/skills/demo/scripts/hello.py",
                "/home/user/skills/demo",
                30);

        verify(sandbox).execute(
                any(),
                eq("cd 'skills/demo' && python3 /home/user/skills/demo/scripts/hello.py"),
                eq(30));
        assertTrue(out.contains("Exit code: 0"));
        assertTrue(out.contains("SKILL_OK"));
    }

    @Test
    void rejectsPathOutsideWorkspace() {
        AbstractSandboxFilesystem sandbox = mock(AbstractSandboxFilesystem.class);
        WorkspaceShellTool tool = new WorkspaceShellTool(sandbox, "/home/user");

        String out = tool.execute(null, "pwd", "/tmp", 30);

        assertEquals(WorkspaceRelativePaths.REJECTED, out);
        verify(sandbox, never()).execute(any(), any(), any());
    }
}
