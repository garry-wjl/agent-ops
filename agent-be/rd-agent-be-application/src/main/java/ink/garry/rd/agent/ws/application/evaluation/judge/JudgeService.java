package ink.garry.rd.agent.ws.application.evaluation.judge;

/**
 * 评测判分服务（M2 起按 Judge 模式拆分多实现）：根据期望输出与实际输出给出 PASS / FAIL。
 * <p>
 * 默认实现：{@link KeywordJudgeServiceImpl}（关键词命中即通过）；
 * LLM_JUDGE / RULE 等多策略待 M2 评测维度白皮书定稿后补齐。
 */
public interface JudgeService {

    /**
     * 判分单条用例。
     *
     * @param expectedOutput 期望输出文本，可为空（空时视为不校验、自动通过）
     * @param actualOutput   实际输出文本
     * @return 判分结果（含 passed 与可读 explanation）
     */
    JudgeResult judge(String expectedOutput, String actualOutput);

    /**
     * 判分结果值对象：是否通过 + 简短解释（用于落 evaluation_case.judge_result）。
     */
    record JudgeResult(boolean passed, String explanation) {}
}
