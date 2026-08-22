package ink.garry.rd.agent.ws.domain.evaluation.grader.gateway;

/**
 * 评估器出站网关：引用检查等。
 */
public interface EvalGraderGateway {

    /**
     * 统计仍绑定该评估器的运行中任务数。
     *
     * @param graderNum 评估器编号
     * @return 运行中任务数
     */
    int countRunningTasksByGrader(String graderNum);
}
