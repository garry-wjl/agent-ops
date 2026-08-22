package ink.garry.rd.agent.ws.domain.evaluation.task.repository;

import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;

/**
 * 评测任务聚合仓储：仅 save / findByNum / deleteByNum。
 */
public interface EvalTaskRepository {
    void save(EvalTask aggregate);
    EvalTask findByNum(String num);
    void deleteByNum(String num);
}
