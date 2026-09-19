package ink.garry.rd.agent.ws.application.agentrunner.harness;

import ink.garry.rd.agent.ws.client.agent.CompactionSetting;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessRuntimeOptionsTest {

    @Test
    void recordLongTermMemory_onlyExplicitTrue() {
        assertFalse(HarnessRuntimeOptions.recordLongTermMemory(null));
        assertFalse(HarnessRuntimeOptions.recordLongTermMemory(false));
        assertTrue(HarnessRuntimeOptions.recordLongTermMemory(true));
    }

    @Test
    void compaction_off_doesNotFlushMemory() {
        CompactionConfig config = HarnessRuntimeOptions.compaction(false, null);
        assertFalse(config.isFlushBeforeCompact());
        assertEquals(50, config.getTriggerMessages());
        assertEquals(20, config.getKeepMessages());
        assertEquals(CompactionConfig.DEFAULT_SUMMARY_PROMPT, config.getSummaryPrompt());
    }

    @Test
    void compaction_on_usesEditedPolicy() {
        CompactionSetting setting = new CompactionSetting();
        setting.setTriggerMessages(12);
        setting.setTriggerTokens(8000);
        setting.setKeepMessages(4);
        setting.setSummaryPrompt("只保留用户事实");

        CompactionConfig config = HarnessRuntimeOptions.compaction(true, setting);

        assertTrue(config.isFlushBeforeCompact());
        assertEquals(12, config.getTriggerMessages());
        assertEquals(8000, config.getTriggerTokens());
        assertEquals(4, config.getKeepMessages());
        assertEquals("只保留用户事实", config.getSummaryPrompt());
    }
}
