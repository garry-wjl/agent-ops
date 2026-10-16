package ink.garry.rd.agent.ws.application.session;

import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.factory.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建会话不得领取沙箱；容器留给首次真实 exec。
 */
@ExtendWith(MockitoExtension.class)
class SessionCommandServiceCreateTest {

    @Mock
    private SessionFactory sessionFactory;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private SandboxPoolService sandboxPoolService;
    @Mock
    private Session session;

    @InjectMocks
    private SessionCommandService sessionCommandService;

    @Test
    void createSession_doesNotBindSandbox() {
        AgentDTO agent = new AgentDTO();
        agent.setNum("AGT1");
        agent.setStatus(AgentStatus.PUBLISHED.name());
        agent.setCreationMode(CreationMode.CONFIG.name());
        agent.setCurrentVersionNum("v1.0.0");
        when(agentQueryService.findAgentByNum("AGT1")).thenReturn(agent);
        when(sessionFactory.createSession(
                anyString(), anyString(), nullable(String.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(session);
        when(session.getNum()).thenReturn("SES1");
        when(session.getAgentNum()).thenReturn("AGT1");

        SessionDTO dto = sessionCommandService.createSession("AGT1", null, "t", "u1", "DEBUG_CONSOLE");

        assertEquals("SES1", dto.getNum());
        verify(session).save("u1");
        verify(sandboxPoolService, never()).ensureBound(any(), any(), any());
        verify(sandboxPoolService, never()).ensureBound(any(), any(), any(), any());
    }
}
