package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 模型用量映射：总 token 取输入加输出，没回用量时保持空。
 */
class TurnTokenUsageMapperTest {

    @Test
    void from_copiesTokenCounts() {
        ChatUsage usage = ChatUsage.builder()
                .inputTokens(30)
                .outputTokens(13)
                .cachedTokens(3)
                .time(1.5)
                .build();

        TurnTokenUsage turn = TurnTokenUsageMapper.from(usage);

        assertEquals(30, turn.getInputTokens());
        assertEquals(13, turn.getOutputTokens());
        assertEquals(3, turn.getCachedTokens());
        assertEquals(43, turn.getTotalTokens());
    }

    @Test
    void from_nullStaysNull() {
        assertNull(TurnTokenUsageMapper.from(null));
    }
}
