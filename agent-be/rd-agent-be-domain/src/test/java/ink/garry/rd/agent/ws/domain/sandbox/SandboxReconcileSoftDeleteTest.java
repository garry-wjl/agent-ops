package ink.garry.rd.agent.ws.domain.sandbox;

import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRepository;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

/**
 * 对账软删：在线 + 已绑定远程 id → deleted=1 + SANDBOX_DELETED。
 */
@ExtendWith(MockitoExtension.class)
class SandboxReconcileSoftDeleteTest {

    @Mock
    private SandboxRepository sandboxRepository;
    @Mock
    private SandboxGateway sandboxGateway;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private Sandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new Sandbox();
        sandbox.setSandboxRepository(sandboxRepository);
        sandbox.setSandboxGateway(sandboxGateway);
        sandbox.setDomainEventPublisher(domainEventPublisher);
        sandbox.setNum("SBX1");
        sandbox.setWorkspaceNum("WS1");
        sandbox.setName("box");
        sandbox.setType(SandboxType.CODE);
        sandbox.setCpu(new BigDecimal("0.5"));
        sandbox.setMemoryMb(1024);
        sandbox.setAliveMinutes(10);
        sandbox.setStatus(SandboxStatus.ONLINE);
        sandbox.setSandboxInstanceId("os-dead");
        sandbox.setDeleted(0);
    }

    @Test
    void reconcileSoftDeleteRemoteGone_softDeletesAndPublishes() {
        sandbox.reconcileSoftDeleteRemoteGone("system");

        assertEquals(1, sandbox.getDeleted());
        assertEquals(SandboxStatus.OFFLINE, sandbox.getStatus());
        verify(sandboxRepository).save(sandbox);
        verify(sandboxRepository).deleteByNum("SBX1");
        ArgumentCaptor<DomainEventDTO> cap = ArgumentCaptor.forClass(DomainEventDTO.class);
        verify(domainEventPublisher).send(cap.capture());
        assertEquals(DomainEventConstant.SANDBOX_DELETED, cap.getValue().getType());
    }

    @Test
    void reconcileSoftDeleteRemoteGone_requiresInstanceId() {
        sandbox.setSandboxInstanceId(null);
        assertThrows(IllegalArgumentException.class,
                () -> sandbox.reconcileSoftDeleteRemoteGone("system"));
    }
}
