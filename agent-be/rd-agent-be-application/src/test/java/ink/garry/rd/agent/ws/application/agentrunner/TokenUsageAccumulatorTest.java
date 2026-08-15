package ink.garry.rd.agent.ws.application.agentrunner;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link TokenUsageAccumulator}：汇总 REASONING 末帧 usage，并回填 AGENT_RESULT。
 */
class TokenUsageAccumulatorTest {

    @Test
    void shouldAggregateReasoningLastUsagesAndApplyToAgentResult() {
        TokenUsageAccumulator acc = new TokenUsageAccumulator();
        acc.accept(reasoningLast(usage(10, 5, 1, 0.5)));
        acc.accept(reasoningChunk(usage(99, 99, 0, 9))); // chunk 不计
        acc.accept(reasoningLast(usage(20, 8, 2, 1.0)));

        Event result = acc.ensureOnAgentResult(agentResult(null));
        ChatUsage usage = result.getMessage().getChatUsage();
        assertNotNull(usage);
        assertEquals(30, usage.getInputTokens());
        assertEquals(13, usage.getOutputTokens());
        assertEquals(3, usage.getCachedTokens());
        assertEquals(1.5, usage.getTime(), 1e-9);
        assertEquals(43, usage.getTotalTokens());
    }

    @Test
    void shouldFallbackToAgentResultUsageWhenNoReasoningUsage() {
        TokenUsageAccumulator acc = new TokenUsageAccumulator();
        ChatUsage existing = usage(7, 3, 0, 0.2);
        Event original = agentResult(existing);
        Event out = acc.ensureOnAgentResult(original);
        assertSame(original, out);
        assertEquals(7, out.getMessage().getChatUsage().getInputTokens());
        assertEquals(3, out.getMessage().getChatUsage().getOutputTokens());
    }

    @Test
    void shouldPreferAggregatedOverPartialAgentResultUsage() {
        TokenUsageAccumulator acc = new TokenUsageAccumulator();
        acc.accept(reasoningLast(usage(10, 5, 0, 0.1)));
        acc.accept(reasoningLast(usage(20, 8, 0, 0.2)));
        // AGENT_RESULT 常只带末次 LLM 用量
        Event out = acc.ensureOnAgentResult(agentResult(usage(20, 8, 0, 0.2)));
        assertEquals(30, out.getMessage().getChatUsage().getInputTokens());
        assertEquals(13, out.getMessage().getChatUsage().getOutputTokens());
    }

    @Test
    void shouldLeaveEventUntouchedWhenNoUsageAnywhere() {
        TokenUsageAccumulator acc = new TokenUsageAccumulator();
        Event original = agentResult(null);
        assertSame(original, acc.ensureOnAgentResult(original));
        assertNull(original.getMessage().getChatUsage());
    }

    private static ChatUsage usage(int in, int out, int cached, double time) {
        return ChatUsage.builder()
                .inputTokens(in)
                .outputTokens(out)
                .cachedTokens(cached)
                .time(time)
                .build();
    }

    private static Event reasoningLast(ChatUsage usage) {
        return new Event(EventType.REASONING, msg(usage), true);
    }

    private static Event reasoningChunk(ChatUsage usage) {
        return new Event(EventType.REASONING, msg(usage), false);
    }

    private static Event agentResult(ChatUsage usage) {
        return new Event(EventType.AGENT_RESULT, msg(usage), true);
    }

    private static Msg msg(ChatUsage usage) {
        return Msg.builder()
                .id("m1")
                .name("agent")
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("ok").build())
                .usage(usage)
                .build();
    }
}
