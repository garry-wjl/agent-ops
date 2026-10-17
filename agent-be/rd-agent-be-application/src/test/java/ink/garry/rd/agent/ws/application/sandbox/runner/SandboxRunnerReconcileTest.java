package ink.garry.rd.agent.ws.application.sandbox.runner;

import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * reconcile：ACS 规格（无 instanceId）只清 runtime；绑定远程 id 且已死则软删资产。
 */
@ExtendWith(MockitoExtension.class)
class SandboxRunnerReconcileTest {

    @Mock
    private SandboxClient sandboxClient;
    @Mock
    private SandboxCommandService sandboxCommandService;
    @Mock
    private SandboxPoolService sandboxPoolService;
    @Mock
    private SandboxContainerGateway sandboxContainerGateway;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private SandboxRunner sandboxRunner;

    @Test
    void reconcile_blankInstanceId_doesNotSoftDeleteAsset() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(0);

        sandboxRunner.reconcile("SBX1", null, "system");

        verify(sandboxPoolService).cleanupDeadInstances("SBX1");
        verify(sandboxPoolService).replenish(eq("SBX1"), eq("system"));
        verify(sandboxCommandService, never()).reconcileSoftDeleteRemoteGone(anyString(), anyString());
        verify(sandboxCommandService, never()).reconcileToOffline(anyString(), anyString());
        verify(sandboxContainerGateway, never()).isAlive(anyString());
    }

    @Test
    void reconcile_aliveInstance_doesNotSoftDelete() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(1);
        when(sandboxContainerGateway.isAlive("os-alive")).thenReturn(true);

        sandboxRunner.reconcile("SBX1", "os-alive", "system");

        verify(sandboxPoolService).cleanupDeadInstances("SBX1");
        verify(sandboxPoolService).replenish("SBX1", "system");
        verify(sandboxCommandService, never()).reconcileSoftDeleteRemoteGone(anyString(), anyString());
    }

    @Test
    void reconcile_deadInstance_softDeletesAsset() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(0);
        when(sandboxContainerGateway.isAlive("os-dead")).thenReturn(false);

        sandboxRunner.reconcile("SBX1", "os-dead", "system");

        verify(sandboxCommandService).reconcileSoftDeleteRemoteGone("SBX1", "system");
        verify(sandboxPoolService, never()).replenish(anyString(), anyString());
    }

    @Test
    void reconcile_isAliveThrows_skipsSoftDelete() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(0);
        when(sandboxContainerGateway.isAlive("os-x")).thenThrow(new RuntimeException("timeout"));

        sandboxRunner.reconcile("SBX1", "os-x", "system");

        verify(sandboxCommandService, never()).reconcileSoftDeleteRemoteGone(anyString(), anyString());
        verify(sandboxPoolService, never()).replenish(anyString(), anyString());
    }
}
