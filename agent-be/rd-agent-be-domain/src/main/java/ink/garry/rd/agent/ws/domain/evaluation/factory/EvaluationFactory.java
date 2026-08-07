package ink.garry.rd.agent.ws.domain.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;

/**
 * Evaluation 聚合工厂：构造装配完整依赖的评测实例。
 */
public interface EvaluationFactory {
    /**
     * 创建一个全新的评测任务（尚未落库），状态默认 PENDING。
     *
     * @param name             评测名称
     * @param agentNum         被评测 Agent 编号
     * @param agentVersionNum  被评测 Agent 版本编号
     * @param skillNum         被评测 Skill 编号，可空
     * @param creatorUserId    评测发起人 ID
     * @return 已装配依赖的 Evaluation 实例
     */
    Evaluation create(String name, String agentNum, String agentVersionNum, String skillNum, String creatorUserId);

    /**
     * 按业务编号加载评测并装配依赖；不存在时返回 null。
     *
     * @param num 评测业务编号
     */
    Evaluation createByNum(String num);
}
