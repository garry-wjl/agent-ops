package ink.garry.rd.agent.ws.domain.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;

/**
 * EvaluationCase 聚合工厂：构造装配完整依赖的执行用例实例。
 */
public interface EvaluationCaseFactory {
    /**
     * 创建一个全新的执行用例（尚未落库）。
     *
     * @param evaluationNum   所属评测编号
     * @param input           用例输入
     * @param expectedOutput  期望输出
     * @return 已装配依赖的 EvaluationCase 实例
     */
    EvaluationCase create(String evaluationNum, String input, String expectedOutput);

    /**
     * 按业务编号加载用例并装配依赖；不存在时返回 null。
     *
     * @param num 用例业务编号
     */
    EvaluationCase createByNum(String num);
}
