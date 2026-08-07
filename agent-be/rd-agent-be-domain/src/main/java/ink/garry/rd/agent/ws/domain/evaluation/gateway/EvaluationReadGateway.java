package ink.garry.rd.agent.ws.domain.evaluation.gateway;

import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;

import java.util.List;

/**
 * 评测域只读查询网关：承接非命令场景的列表/分页/统计读取，
 * 与仓储分离以保持仓储仅负责 save/findByNum/deleteByNum。
 */
public interface EvaluationReadGateway {
    /**
     * 按多维条件分页查询评测任务。
     *
     * @param condition 查询条件（agentNum/skillNum/status/分页参数）
     * @return 分页结果（total + list）
     */
    PageResult<Evaluation> pageQuery(EvaluationPageCondition condition);

    /**
     * 列出指定评测下的全部用例。
     *
     * @param evaluationNum 评测业务编号
     * @return 用例列表
     */
    List<EvaluationCase> listCases(String evaluationNum);

    /**
     * 列出指定 Skill 下的若干种子用例。
     *
     * @param skillNum Skill 业务编号
     * @param limit    最大条数
     * @return 种子列表
     */
    List<EvalSeed> listSeeds(String skillNum, int limit);

    /**
     * 评测看板汇总统计。
     *
     * @return 评测数 / 用例数 / 平均通过率
     */
    DashboardStats stats();

    /** 通用分页结果：total + list。 */
    record PageResult<T>(Long total, List<T> list) {}

    /** 评测分页查询条件，status 为字符串以便兼容前端原值。 */
    record EvaluationPageCondition(String agentNum, String skillNum, String status, Integer pageNo, Integer pageSize) {}

    /** 评测看板统计：评测数、用例数、平均通过率（0-1 之间的小数）。 */
    record DashboardStats(Long evaluationCount, Long caseCount, Double averagePassRate) {}
}
