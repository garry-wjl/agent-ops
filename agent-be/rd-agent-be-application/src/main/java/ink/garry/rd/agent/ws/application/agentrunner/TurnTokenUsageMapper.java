package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import io.agentscope.core.model.ChatUsage;

/**
 * 把模型返回的 {@link ChatUsage} 收成可落库的一轮用量。
 */
final class TurnTokenUsageMapper {

    private TurnTokenUsageMapper() {
    }

    /**
     * @param usage 本轮汇总用量；模型没回时为 null
     * @return 可持久化的用量；入参为空时返回 null
     */
    static TurnTokenUsage from(ChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return TurnTokenUsage.builder()
                .inputTokens(usage.getInputTokens())
                .outputTokens(usage.getOutputTokens())
                .cachedTokens(usage.getCachedTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }
}
