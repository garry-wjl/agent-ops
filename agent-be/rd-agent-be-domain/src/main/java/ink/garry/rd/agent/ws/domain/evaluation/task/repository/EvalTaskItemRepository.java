package ink.garry.rd.agent.ws.domain.evaluation.task.repository;

import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;

/**
 * 评测任务用例仓储：仅 save / findByNum / deleteByNum。
 */
public interface EvalTaskItemRepository {
    void save(EvalTaskItem aggregate);
    EvalTaskItem findByNum(String num);
    void deleteByNum(String num);
}
