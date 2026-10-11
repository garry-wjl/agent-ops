package ink.garry.rd.agent.ws.application.agentrunner.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 续传 lastEventId / run 解析。
 */
class AgentRunCursorParseTest {

    private AgentRunEventStore eventStore;
    private AgentRunCoordinator coordinator;

    @BeforeEach
    void setUp() {
        eventStore = mock(AgentRunEventStore.class);
        coordinator = new AgentRunCoordinator(
                eventStore,
                mock(AgentRunnerService.class),
                mock(SessionCommandService.class),
                new ObjectMapper());
    }

    @Test
    void resume_invalidCursor_throws() {
        assertThatThrownBy(() -> coordinator.resume("SES1", null, "bad-id").blockFirst())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BizCode.CURSOR_INVALID.getCode()));
    }

    @Test
    void resume_missingRun_throwsNotFound() {
        when(eventStore.findRunningRunId("SES1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> coordinator.resume("SES1", null, null).blockFirst())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BizCode.RUN_NOT_FOUND.getCode()));
    }

    @Test
    void resume_expiredMeta_throwsCursorExpired() {
        when(eventStore.getMeta("RUNgone")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> coordinator.resume("SES1", null, "RUNgone:3").blockFirst())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BizCode.CURSOR_EXPIRED.getCode()));
    }
}
