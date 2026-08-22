package ink.garry.rd.agent.ws.domain.evaluation.task.gateway;

import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.EvalItemScore;

import java.util.List;

/**
 * 评测任务出站网关：用例得分落库等。
 */
public interface EvalTaskGateway {

    /**
     * 覆盖写入某用例的评估器得分明细。
     *
     * @param taskItemNum 用例编号
     * @param scores 得分列表
     */
    void replaceItemScores(String taskItemNum, List<EvalItemScore> scores);
}
