package ink.garry.rd.agent.ws.domain.evaluation.repository;

import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;

/**
 * EvaluationCase 聚合仓储：仅承担命令侧持久化与按编号读取。
 */
public interface EvaluationCaseRepository {
    /**
     * 新增或更新执行用例。
     *
     * @param aggregate 待保存的用例聚合根
     */
    void save(EvaluationCase aggregate);

    /**
     * 按业务编号加载执行用例。
     *
     * @param num 用例业务编号
     * @return 实体；不存在时返回 null
     */
    EvaluationCase findByNum(String num);

    /**
     * 按业务编号删除用例（软删/物理删由实现决定）。
     *
     * @param num 用例业务编号
     */
    void deleteByNum(String num);
}
