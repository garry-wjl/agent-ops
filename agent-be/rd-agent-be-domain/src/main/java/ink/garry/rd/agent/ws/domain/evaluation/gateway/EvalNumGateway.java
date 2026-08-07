package ink.garry.rd.agent.ws.domain.evaluation.gateway;

/**
 * 评测域编号生成网关：统一分配 evaluation/evaluationCase/evalSeed 业务编号。
 */
public interface EvalNumGateway {
    /**
     * 生成新的评测业务编号。
     *
     * @return 全局唯一的 evaluationNum
     */
    String generateEvaluationNum();

    /**
     * 生成新的评测用例业务编号。
     *
     * @return 全局唯一的 evaluationCaseNum
     */
    String generateEvaluationCaseNum();

    /**
     * 生成新的种子用例业务编号。
     *
     * @return 全局唯一的 evalSeedNum
     */
    String generateEvalSeedNum();
}
