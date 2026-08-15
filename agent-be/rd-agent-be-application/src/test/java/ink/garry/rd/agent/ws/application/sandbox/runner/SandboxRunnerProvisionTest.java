package ink.garry.rd.agent.ws.application.sandbox.runner;

import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.sandbox.dto.SandboxDomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SandboxRunner 供给路径：断言就绪检查走 assertReady，失败回写 FAILED。
 */
@ExtendWith(MockitoExtension.class)
class SandboxRunnerProvisionTest {

    @Mock
    private SandboxClient sandboxClient;

    @Mock
    private SandboxCommandService sandboxCommandService;

    @InjectMocks
    private SandboxRunner sandboxRunner;

    @Test
    void provision_success_shouldAssertReadyThenOnline() {
        when(sandboxClient.create(eq(BigDecimal.ONE), eq(512), eq(10))).thenReturn("sbx-1");
        doNothing().when(sandboxClient).assertReady("sbx-1");

        sandboxRunner.provision(submittedEvent());

        verify(sandboxClient).assertReady("sbx-1");
        verify(sandboxCommandService).onlineSandbox("SBX1", "sbx-1", "u1");
        verify(sandboxCommandService, never()).markProvisionFailed(anyString(), anyString(), anyString());
        verify(sandboxClient, never()).kill(anyString());
    }

    @Test
    void provision_assertReadyFails_shouldKillAndMarkFailed() {
        when(sandboxClient.create(eq(BigDecimal.ONE), eq(512), eq(10))).thenReturn("sbx-2");
        doThrow(new IllegalStateException("sandbox execd ping failed, id=sbx-2"))
                .when(sandboxClient).assertReady("sbx-2");

        sandboxRunner.provision(submittedEvent());

        verify(sandboxClient).assertReady("sbx-2");
        verify(sandboxClient).kill("sbx-2");
        verify(sandboxCommandService).markProvisionFailed(
                eq("SBX1"), eq("sandbox execd ping failed, id=sbx-2"), eq("u1"));
        verify(sandboxCommandService, never()).onlineSandbox(anyString(), anyString(), anyString());
    }

    @Test
    void provision_createFails_shouldMarkFailedWithoutKill() {
        when(sandboxClient.create(any(BigDecimal.class), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("create refused"));

        sandboxRunner.provision(submittedEvent());

        verify(sandboxClient, never()).assertReady(anyString());
        verify(sandboxClient, never()).kill(anyString());
        verify(sandboxCommandService).markProvisionFailed(eq("SBX1"), eq("create refused"), eq("u1"));
        verify(sandboxCommandService, never()).onlineSandbox(anyString(), anyString(), anyString());
    }

    private static DomainEventDTO submittedEvent() {
        SandboxDomainEventDTO payload = SandboxDomainEventDTO.builder()
                .num("SBX1")
                .cpu(BigDecimal.ONE)
                .memoryMb(512)
                .aliveMinutes(10)
                .operatorEmpNo("u1")
                .build();
        DomainEventDTO event = new DomainEventDTO();
        event.setType(DomainEventConstant.SANDBOX_SUBMITTED);
        event.setData(payload);
        return event;
    }
}
