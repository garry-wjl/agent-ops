package ink.garry.rd.agent.ws.application.sandbox.runner;

import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
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
 * 会话隔离后 reconcile 不再因空白 instanceId 下线资产。
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
    private RedissonClient redissonClient;

    @InjectMocks
    private SandboxRunner sandboxRunner;

    @Test
    void reconcile_blankInstanceId_doesNotOffline() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(0);

        sandboxRunner.reconcile("SBX1", null, "system");

        verify(sandboxPoolService).cleanupDeadInstances("SBX1");
        verify(sandboxPoolService).replenish(eq("SBX1"), eq("system"));
        verify(sandboxCommandService, never()).reconcileToOffline(anyString(), anyString());
        verify(sandboxClient, never()).isAlive(anyString());
    }

    @Test
    void reconcile_cleansDeadThenReplenishes() {
        when(sandboxPoolService.cleanupDeadInstances("SBX1")).thenReturn(2);

        sandboxRunner.reconcile("SBX1", "os-rep", "system");

        verify(sandboxPoolService).cleanupDeadInstances("SBX1");
        verify(sandboxPoolService).replenish("SBX1", "system");
        verify(sandboxCommandService, never()).reconcileToOffline(anyString(), anyString());
    }
}
