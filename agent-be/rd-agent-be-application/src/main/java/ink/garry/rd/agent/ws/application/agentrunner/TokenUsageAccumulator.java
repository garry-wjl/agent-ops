package ink.garry.rd.agent.ws.application.agentrunner;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;

/**
 * 汇总一轮 Agent 调用中的 Token 用量。
 * <p>
 * 策略：累加每次 {@code REASONING + isLast} 上的 {@link ChatUsage}（对应一次 LLM 调用），
 * 避免再把 {@code AGENT_RESULT} 上的末次用量二次计入。若全程无 REASONING 用量，则回退到
 * {@code AGENT_RESULT.message.usage}。
 */
final class TokenUsageAccumulator {

    private int inputTokens;
    private int outputTokens;
    private int cachedTokens;
    private double time;
    private boolean hasReasoningUsage;

    /**
     * 从流事件中采集用量（仅 REASONING 末帧）。
     *
     * @param event AgentScope 事件，可空
     */
    void accept(Event event) {
        if (event == null || event.getType() != EventType.REASONING || !event.isLast()) {
            return;
        }
        add(usageOf(event.getMessage()));
    }

    /**
     * 确保 {@code AGENT_RESULT} 末帧携带本轮汇总 usage；其他事件原样返回。
     *
     * @param event 原始事件
     * @return 可能已回填 usage 的事件
     */
    Event ensureOnAgentResult(Event event) {
        if (event == null
                || event.getType() != EventType.AGENT_RESULT
                || !event.isLast()
                || event.getMessage() == null) {
            return event;
        }
        ChatUsage aggregated = snapshot();
        ChatUsage existing = usageOf(event.getMessage());
        ChatUsage toApply = aggregated != null ? aggregated : existing;
        if (toApply == null) {
            return event;
        }
        if (existing != null && sameUsage(existing, toApply)) {
            return event;
        }
        return withUsage(event, toApply);
    }

    /**
     * 当前汇总快照；尚无 REASONING 用量时返回 null。
     */
    ChatUsage snapshot() {
        if (!hasReasoningUsage) {
            return null;
        }
        return ChatUsage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cachedTokens(cachedTokens)
                .time(time)
                .build();
    }

    private void add(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens += usage.getInputTokens();
        outputTokens += usage.getOutputTokens();
        cachedTokens += usage.getCachedTokens();
        time += usage.getTime();
        hasReasoningUsage = true;
    }

    private static ChatUsage usageOf(Msg msg) {
        return msg == null ? null : msg.getChatUsage();
    }

    private static boolean sameUsage(ChatUsage a, ChatUsage b) {
        return a.getInputTokens() == b.getInputTokens()
                && a.getOutputTokens() == b.getOutputTokens()
                && a.getCachedTokens() == b.getCachedTokens()
                && Double.compare(a.getTime(), b.getTime()) == 0;
    }

    private static Event withUsage(Event event, ChatUsage usage) {
        Msg msg = event.getMessage();
        Msg newMsg = Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(msg.getContent())
                .metadata(msg.getMetadata())
                .timestamp(msg.getTimestamp())
                .usage(usage)
                .build();
        return new Event(event.getType(), newMsg, event.isLast(), event.getSource());
    }
}
