package ink.garry.rd.agent.ws.application.evaluation.casegen;

import java.util.List;

/**
 * 评测用例生成器：依据 dataSource 策略生成评测输入集（input + expectedOutput 对）。
 * <p>
 * 默认实现：{@link SeedCaseGenerator}（从 eval_seed 表取）；
 * SCHEMA_GEN / HISTORY 待评测技术方案 §11.1 CaseGenerator 三实现补齐。
 */
public interface CaseGenerator {

    /**
     * 为指定 Skill 生成最多 limit 条用例。
     *
     * @param skillNum 关联 Skill 业务编号
     * @param limit    用例上限（建议 ≤ 50 与 evaluation.case_count 上限对齐）
     * @return 用例草料；不足 limit 时返回实际可用条目
     */
    List<CaseSeed> generate(String skillNum, int limit);

    /**
     * 用例草料：未持久化前的输入 + 期望输出对。
     */
    record CaseSeed(String input, String expectedOutput) {}
}
