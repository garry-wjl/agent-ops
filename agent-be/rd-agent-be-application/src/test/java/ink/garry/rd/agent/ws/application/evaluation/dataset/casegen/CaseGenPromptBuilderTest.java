package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseGenPromptBuilderTest {

    @Test
    void agentTypeIncludesUnderTestSection() {
        String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                "ds1", "desc", "AGENT", "[{\"name\":\"input\"}]", 5,
                "AGT1", "助手", "desc2", "你是助手", "APPEND", null));
        assertTrue(prompt.contains("被测 Agent"));
        assertTrue(prompt.contains("AGT1"));
        assertTrue(prompt.contains("恰好 5 条"));
        assertTrue(prompt.contains("JSON 数组"));
    }

    @Test
    void customTypeOmitsUnderTestSection() {
        String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                "ds1", null, "CUSTOM", "[]", null,
                "AGT1", "助手", null, null, "APPEND", "补充说明X"));
        assertFalse(prompt.contains("被测 Agent"));
        assertTrue(prompt.contains("自行决定合理数量"));
        assertTrue(prompt.contains("补充说明X"));
    }

    @Test
    void overrideKeepsSchemaAndFormat() {
        String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                "ds1", null, "CUSTOM", "[{\"name\":\"input\"}]", 3,
                null, null, null, null, "OVERRIDE", "只用我的说明"));
        assertTrue(prompt.contains("只用我的说明"));
        assertTrue(prompt.contains("Schema"));
        assertTrue(prompt.contains("JSON 数组"));
        assertFalse(prompt.contains("你是评测 Case 生成助手"));
    }

    @Test
    void appendWithoutUserInstruction_stillHasFormat() {
        String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                "ds", "d", "CUSTOM", "[]", null,
                null, null, null, null, "APPEND", "  "));
        assertTrue(prompt.contains("自行决定合理数量"));
        assertFalse(prompt.contains("用户补充说明"));
    }

    @Test
    void truncatesLongSystemPrompt() {
        String longPrompt = "x".repeat(5000);
        String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                "ds", null, "AGENT", "[]", 1,
                "AGT1", "n", null, longPrompt, "APPEND", null));
        assertTrue(prompt.contains("truncated"));
        assertTrue(prompt.length() < 5000 + 2000);
    }
}
