package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

/**
 * 评测报告 VO。
 * <p>
 * 单次评测的汇总统计：总用例数、通过/失败数、通过率。供报告页/列表行的"概览"区使用，
 * 详细用例请见 {@link EvaluationDetailVO#getCases()}。
 */
@Data
public class EvalReportVO {
    /** 评测业务编号 */
    private String evaluationNum;
    /** 总用例数 */
    private Integer totalCaseCount;
    /** 通过用例数 */
    private Integer passedCaseCount;
    /** 失败用例数 */
    private Integer failedCaseCount;
    /** 通过率，范围 [0, 1] = passedCaseCount / totalCaseCount */
    private Double passRate;
}
