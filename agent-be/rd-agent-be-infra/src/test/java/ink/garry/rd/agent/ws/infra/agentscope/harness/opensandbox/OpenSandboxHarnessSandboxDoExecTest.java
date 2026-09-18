package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link OpenSandboxHarnessSandbox#doExec} 映射单测：mock {@link OpenSandboxExecBridge}，
 * 校验 cwd 包装、退出码映射与非 0 抛 {@link SandboxException.ExecException}。
 */
class OpenSandboxHarnessSandboxDoExecTest {

    private OpenSandboxExecBridge bridge;
    private OpenSandboxHarnessSandbox sandbox;

    @BeforeEach
    void setUp() {
        bridge = mock(OpenSandboxExecBridge.class);
        OpenSandboxSandboxState state = new OpenSandboxSandboxState();
        state.setSessionId("hs-1");
        state.setInstanceId("sbx-1");
        state.setSessionNum("sess-1");
        state.setEnv(Map.of("FOO", "bar"));
        state.setTtlMinutes(45L);
        state.setWorkspaceRoot("/workspace");
        WorkspaceSpec spec = new WorkspaceSpec();
        spec.setRoot("/workspace");
        state.setWorkspaceSpec(spec);
        sandbox = new OpenSandboxHarnessSandbox(state, bridge);
    }

    @Test
    void doExec_shouldCdToWorkspaceAndMapStdout() throws Exception {
        when(bridge.exec(eq("sbx-1"), eq("sess-1"), anyMap(), eq(45L), anyString()))
                .thenReturn(new OpenSandboxCommandResult(0, "hello\n", ""));

        ExecResult result = sandbox.exec(null, "echo hello", 30);

        assertEquals(0, result.exitCode());
        assertEquals("hello\n", result.stdout());
        assertEquals("", result.stderr());
        verify(bridge)
                .exec(
                        eq("sbx-1"),
                        eq("sess-1"),
                        eq(Map.of("FOO", "bar")),
                        eq(45L),
                        eq("cd '/workspace' && echo hello"));
    }

    @Test
    void doExec_nonzeroExit_shouldReturnResultWithoutThrowing() throws Exception {
        when(bridge.exec(anyString(), anyString(), anyMap(), anyLong(), anyString()))
                .thenReturn(new OpenSandboxCommandResult(7, "out", "boom"));

        ExecResult result = sandbox.exec(null, "false", 10);
        assertEquals(7, result.exitCode());
        assertEquals("out", result.stdout());
        assertEquals("boom", result.stderr());
    }

    @Test
    void toExecResultOrThrow_nullExitCode_treatedAsFailure() {
        assertThrows(
                SandboxException.ExecException.class,
                () ->
                        OpenSandboxHarnessSandbox.toExecResultOrThrow(
                                new OpenSandboxCommandResult(null, "", "x")));
    }

    @Test
    void shellSingleQuote_escapesEmbeddedQuotes() {
        assertEquals("'a'\\''b'", OpenSandboxHarnessSandbox.shellSingleQuote("a'b"));
        assertTrue(OpenSandboxHarnessSandbox.shellSingleQuote("/workspace").startsWith("'"));
    }
}
