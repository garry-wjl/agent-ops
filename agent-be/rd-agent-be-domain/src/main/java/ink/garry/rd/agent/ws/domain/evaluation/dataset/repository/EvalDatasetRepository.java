package ink.garry.rd.agent.ws.domain.evaluation.dataset.repository;

import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;

/**
 * 评测集聚合仓储：仅 save / findByNum / deleteByNum。
 */
public interface EvalDatasetRepository {

    /** 持久化评测集聚合（upsert）。 */
    void save(EvalDataset aggregate);

    /** 按业务编号加载；不存在返回 null。 */
    EvalDataset findByNum(String num);

    /** 按业务编号软删除。 */
    void deleteByNum(String num);
}
