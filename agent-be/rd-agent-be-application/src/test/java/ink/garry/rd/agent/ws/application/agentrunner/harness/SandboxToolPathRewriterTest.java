package ink.garry.rd.agent.ws.application.agentrunner.harness;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.ExecuteResponse;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.tool.FilesystemTool;
import io.agentscope.harness.agent.tool.ShellExecuteTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxToolPathRewriterTest {

    @Test
    void replacesHarnessExecuteSoAbsoluteWorkspaceDirIsRelative() {
        AbstractSandboxFilesystem sandbox = mock(AbstractSandboxFilesystem.class);
        when(sandbox.execute(any(), any(), any())).thenReturn(new ExecuteResponse("ok\n", 0, false));
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ShellExecuteTool(sandbox));
        toolkit.registerTool(new FilesystemTool(mock(AbstractFilesystem.class)));

        SandboxToolPathRewriter.rewrite(toolkit, "/home/user");

        String out = toolkit.callTool(io.agentscope.core.tool.ToolCallParam.builder()
                        .toolUseBlock(io.agentscope.core.message.ToolUseBlock.builder()
                                .id("1")
                                .name("execute")
                                .content("{\"command\":\"pwd\",\"working_directory\":\"/home/user/skills/demo\",\"timeout\":30}")
                                .input(java.util.Map.of(
                                        "command", "pwd",
                                        "working_directory", "/home/user/skills/demo",
                                        "timeout", 30))
                                .build())
                        .build())
                .block()
                .getOutput()
                .stream()
                .map(b -> b instanceof io.agentscope.core.message.TextBlock tb ? tb.getText() : "")
                .reduce("", String::concat);

        verify(sandbox).execute(any(), eq("cd 'skills/demo' && pwd"), eq(30));
        assertTrue(out.contains("Exit code: 0"), out);
    }
}
