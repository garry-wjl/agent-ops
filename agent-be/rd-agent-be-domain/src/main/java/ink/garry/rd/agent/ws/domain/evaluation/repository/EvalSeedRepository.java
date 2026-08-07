package ink.garry.rd.agent.ws.domain.evaluation.repository;

import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;

/**
 * EvalSeed 聚合仓储：仅承担命令侧持久化与按编号读取。
 */
public interface EvalSeedRepository {
    /**
     * 新增或更新种子用例。
     *
     * @param aggregate 待保存的种子聚合根
     */
    void save(EvalSeed aggregate);

    /**
     * 按业务编号加载种子。
     *
     * @param num 种子业务编号
     * @return 实体；不存在时返回 null
     */
    EvalSeed findByNum(String num);

    /**
     * 按业务编号删除种子（软删/物理删由实现决定）。
     *
     * @param num 种子业务编号
     */
    void deleteByNum(String num);
}
