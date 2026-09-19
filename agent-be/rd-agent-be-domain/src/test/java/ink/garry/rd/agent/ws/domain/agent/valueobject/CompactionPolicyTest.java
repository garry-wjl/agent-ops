package ink.garry.rd.agent.ws.domain.agent.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompactionPolicyTest {

    @Test
    void normalize_defaultsWhenNull() {
        CompactionPolicy p = CompactionPolicy.normalize(null);
        assertEquals(50, p.getTriggerMessages());
        assertEquals(0, p.getTriggerTokens());
        assertEquals(20, p.getKeepMessages());
        assertNull(p.getSummaryPrompt());
    }

    @Test
    void normalize_clampsAndDropsBlankPrompt() {
        CompactionPolicy raw = CompactionPolicy.builder()
                .triggerMessages(0)
                .triggerTokens(9_000_000)
                .keepMessages(500)
                .summaryPrompt("  ")
                .build();
        CompactionPolicy p = CompactionPolicy.normalize(raw);
        assertEquals(1, p.getTriggerMessages());
        assertEquals(2_000_000, p.getTriggerTokens());
        assertEquals(200, p.getKeepMessages());
        assertNull(p.getSummaryPrompt());
    }
}
