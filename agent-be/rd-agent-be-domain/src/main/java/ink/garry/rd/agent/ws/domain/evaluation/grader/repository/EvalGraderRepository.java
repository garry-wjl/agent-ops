package ink.garry.rd.agent.ws.domain.evaluation.grader.repository;

import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;

/**
 * 评估器聚合仓储：仅 save / findByNum / deleteByNum。
 */
public interface EvalGraderRepository {

    /** 持久化评估器。 */
    void save(EvalGrader aggregate);

    /** 按编号加载。 */
    EvalGrader findByNum(String num);

    /** 软删除。 */
    void deleteByNum(String num);
}
