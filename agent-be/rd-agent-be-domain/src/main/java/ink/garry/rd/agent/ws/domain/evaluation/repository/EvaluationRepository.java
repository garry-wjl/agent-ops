package ink.garry.rd.agent.ws.domain.evaluation.repository;

import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;

/**
 * Evaluation 聚合根仓储：仅承担命令侧持久化与按编号读取。
 */
public interface EvaluationRepository {
    /**
     * 新增或更新评测聚合根。
     *
     * @param aggregate 待保存的评测
     */
    void save(Evaluation aggregate);

    /**
     * 按业务编号加载评测。
     *
     * @param num 评测业务编号
     * @return 实体；不存在时返回 null
     */
    Evaluation findByNum(String num);

    /**
     * 按业务编号删除评测（不级联用例）。
     *
     * @param num 评测业务编号
     */
    void deleteByNum(String num);
}
