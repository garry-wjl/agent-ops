package ink.garry.rd.agent.ws.application.agentrunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ink.garry.rd.agent.ws.application.debugconsole.SegmentAccumulator;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 没有 AGENT_RESULT 时，有累积回复就补写，没有就失败。
 */
class AgentRunnerReplyRecoveryTest {

    @Test
    void skipsWhenAssistantAlreadyPersisted() {
        AtomicInteger writes = new AtomicInteger();
        AgentRunnerService.whenStreamEndedWithoutResult(
                        new AtomicBoolean(true), new SegmentAccumulator(), writes::incrementAndGet)
                .block();
        assertThat(writes).hasValue(0);
    }

    @Test
    void persistsAccumulatedReplyWhenResultFrameMissing() {
        SegmentAccumulator acc = new SegmentAccumulator();
        acc.accept(new Event(EventType.REASONING, Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(List.of(TextBlock.builder().text("好").build()))
                .build(), true));
        AtomicBoolean persisted = new AtomicBoolean(false);
        AtomicInteger writes = new AtomicInteger();
        AgentRunnerService.whenStreamEndedWithoutResult(persisted, acc, writes::incrementAndGet)
                .block();
        assertThat(writes).hasValue(1);
        assertThat(persisted).isTrue();
    }

    @Test
    void failsWhenNothingWasProduced() {
        assertThatThrownBy(() -> AgentRunnerService.whenStreamEndedWithoutResult(
                        new AtomicBoolean(false), new SegmentAccumulator(), () -> {
                        }).block())
                .isInstanceOf(BusinessException.class)
                .satisfies(err -> assertThat(((BusinessException) err).getCode())
                        .isEqualTo(BizCode.SSE_INTERRUPTED.getCode()));
    }
}
