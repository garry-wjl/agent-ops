package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.sandbox.SandboxAcquireResult;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import io.agentscope.harness.agent.sandbox.SandboxManager;
import io.agentscope.harness.agent.sandbox.SessionSandboxStateStore;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 方案 A：平台供给容器 shutdown 不删 workspace；
 * 方案 B：externalSandbox → user-managed，release 不触发 shutdown。
 */
class OpenSandboxWorkspacePersistTest {

    private OpenSandboxExecBridge bridge;
    private OpenSandboxHarnessSandbox platformSandbox;

    @BeforeEach
    void setUp() {
        bridge = mock(OpenSandboxExecBridge.class);
        OpenSandboxSandboxState state = new OpenSandboxSandboxState();
        state.setSessionId("hs-1");
        state.setInstanceId("sbx-1");
        state.setSessionNum("sess-1");
        state.setEnv(Map.of());
        state.setTtlMinutes(10L);
        state.setWorkspaceRoot("/workspace");
        state.setContainerOwned(false);
        WorkspaceSpec spec = new WorkspaceSpec();
        spec.setRoot("/workspace");
        state.setWorkspaceSpec(spec);
        platformSandbox = new OpenSandboxHarnessSandbox(state, bridge);
    }

    @Test
    void shutdown_platformOwned_skipsRmWorkspace() throws Exception {
        platformSandbox.shutdown();
        verifyNoInteractions(bridge);
    }

    @Test
    void shutdown_containerOwned_destroysWorkspace() throws Exception {
        OpenSandboxSandboxState state = new OpenSandboxSandboxState();
        state.setSessionId("hs-2");
        state.setInstanceId("sbx-2");
        state.setSessionNum("sess-2");
        state.setTtlMinutes(10L);
        state.setWorkspaceRoot("/workspace");
        state.setContainerOwned(true);
        WorkspaceSpec spec = new WorkspaceSpec();
        spec.setRoot("/workspace");
        state.setWorkspaceSpec(spec);
        OpenSandboxHarnessSandbox owned = new OpenSandboxHarnessSandbox(state, bridge);

        owned.shutdown();

        verify(bridge).exec(
                eq("sbx-2"),
                eq("sess-2"),
                org.mockito.ArgumentMatchers.isNull(),
                eq(10L),
                eq("rm -rf '/workspace'"));
    }

    @Test
    void userManagedContext_acquireIsNotSelfManaged_releaseSkipsDestroy() throws Exception {
        SandboxContext ctx = OpenSandboxUserManagedContextFactory.create(
                bridge, "sbx-1", "sess-1", "SBX1", "AGT1", Map.of(), 10L);
        assertNotNull(ctx);
        assertNotNull(ctx.getExternalSandbox());

        OpenSandboxClient client = new OpenSandboxClient(bridge);
        SessionSandboxStateStore store = mock(SessionSandboxStateStore.class);
        SandboxManager manager = new SandboxManager(client, store, "agent-1");

        SandboxAcquireResult acquired = manager.acquire(ctx, RuntimeContext.empty());
        assertNotNull(acquired);
        assertFalse(acquired.isSelfManaged());

        manager.release(acquired);
        verify(bridge, never()).exec(anyString(), anyString(), anyMap(), anyLong(), contains("rm -rf"));
    }

    @Test
    void userManagedFactory_blankInstanceWithoutSandboxNum_returnsNull() {
        assertTrue(OpenSandboxUserManagedContextFactory.create(
                bridge, " ", "sess-1", null, null, null, 10L) == null);
    }

    @Test
    void userManagedFactory_blankInstanceWithSandboxNum_allowsLazyBind() {
        SandboxContext ctx = OpenSandboxUserManagedContextFactory.create(
                bridge, null, "sess-1", "SBX1", "AGT1", Map.of(), 10L);
        assertNotNull(ctx);
        assertNotNull(ctx.getExternalSandbox());
        OpenSandboxHarnessSandbox sbx = (OpenSandboxHarnessSandbox) ctx.getExternalSandbox();
        assertTrue(sbx.getOpenSandboxState().getInstanceId() == null
                || sbx.getOpenSandboxState().getInstanceId().isBlank());
        assertTrue("SBX1".equals(sbx.getOpenSandboxState().getSandboxNum()));
    }

    @Test
    void openSandboxClient_create_blankInstanceRequiresSandboxNum() {
        OpenSandboxClient client = new OpenSandboxClient(bridge);
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot("/workspace");
        OpenSandboxClientOptions options = new OpenSandboxClientOptions()
                .execBridge(bridge)
                .sessionNum("sess-x")
                .sandboxNum("SBX-X")
                .agentNum("AGT-X");
        assertNotNull(client.create(workspaceSpec, new io.agentscope.harness.agent.sandbox.snapshot.NoopSnapshotSpec(), options));
    }

    @Test
    void start_doesNotTouchBridge_evenWhenInstanceBlank() throws Exception {
        OpenSandboxSandboxState state = new OpenSandboxSandboxState();
        state.setSessionId("hs-lazy");
        state.setSessionNum("sess-lazy");
        state.setSandboxNum("SBX1");
        state.setAgentNum("AGT1");
        state.setTtlMinutes(10L);
        state.setWorkspaceRoot("/workspace");
        state.setContainerOwned(false);
        WorkspaceSpec spec = new WorkspaceSpec();
        spec.setRoot("/workspace");
        state.setWorkspaceSpec(spec);
        OpenSandboxHarnessSandbox lazy = new OpenSandboxHarnessSandbox(state, bridge);

        lazy.start();

        assertTrue(lazy.isRunning());
        verifyNoInteractions(bridge);
    }

    @Test
    void acquireStart_userManaged_doesNotEnsureOnStart() throws Exception {
        SandboxContext ctx = OpenSandboxUserManagedContextFactory.create(
                bridge, null, "sess-1", "SBX1", "AGT1", Map.of(), 10L);
        assertNotNull(ctx);

        OpenSandboxClient client = new OpenSandboxClient(bridge);
        SessionSandboxStateStore store = mock(SessionSandboxStateStore.class);
        SandboxManager manager = new SandboxManager(client, store, "agent-1");

        SandboxAcquireResult acquired = manager.acquire(ctx, RuntimeContext.empty());
        assertNotNull(acquired);
        acquired.getSandbox().start();

        verify(bridge, never()).resolveInstanceId(
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class));
        verify(bridge, never()).exec(
                nullable(String.class), anyString(), anyMap(), anyLong(), anyString());
    }

    @Test
    void firstDoExec_resolvesAndMkdirs() throws Exception {
        when(bridge.resolveInstanceId(eq("sbx-1"), eq("sess-1"), isNull(), isNull()))
                .thenReturn("sbx-1");
        when(bridge.exec(eq("sbx-1"), eq("sess-1"), anyMap(), eq(10L), contains("mkdir -p")))
                .thenReturn(new OpenSandboxCommandResult(0, "", ""));
        when(bridge.exec(eq("sbx-1"), eq("sess-1"), anyMap(), eq(10L), contains("echo hi")))
                .thenReturn(new OpenSandboxCommandResult(0, "ok", ""));

        platformSandbox.start();
        verifyNoInteractions(bridge);

        var result = platformSandbox.exec(RuntimeContext.empty(), "echo hi", 30);
        assertNotNull(result);
        assertTrue(result.ok());

        verify(bridge).resolveInstanceId(eq("sbx-1"), eq("sess-1"), isNull(), isNull());
        verify(bridge).exec(eq("sbx-1"), eq("sess-1"), anyMap(), eq(10L), contains("mkdir -p"));
        verify(bridge).exec(eq("sbx-1"), eq("sess-1"), anyMap(), eq(10L), contains("echo hi"));
    }
}
