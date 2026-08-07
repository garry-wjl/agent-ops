package ink.garry.rd.agent.ws.domain.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;

/**
 * EvalSeed 聚合工厂：构造装配完整依赖的种子实例。
 */
public interface EvalSeedFactory {
    /**
     * 创建一个全新的种子（尚未落库）。
     *
     * @param skillNum        关联 Skill 编号
     * @param input           种子输入
     * @param expectedOutput  期望输出
     * @return 已装配依赖的 EvalSeed 实例
     */
    EvalSeed create(String skillNum, String input, String expectedOutput);

    /**
     * 按业务编号加载种子并装配依赖；不存在时返回 null。
     *
     * @param num 种子业务编号
     */
    EvalSeed createByNum(String num);
}
