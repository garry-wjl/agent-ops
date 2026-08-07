package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

/**
 * 评测对比 VO。
 * <p>
 * 对比"基线"与"候选"两次评测的通过率，输出绝对差值（candidate - baseline）。
 * 供回归/AB 对比页面使用，正值表示候选优于基线。
 */
@Data
public class EvalCompareVO {
    /** 基线评测业务编号 */
    private String baselineEvaluationNum;
    /** 候选评测业务编号 */
    private String candidateEvaluationNum;
    /** 基线通过率，范围 [0, 1] */
    private Double baselinePassRate;
    /** 候选通过率，范围 [0, 1] */
    private Double candidatePassRate;
    /** 通过率差值 = candidatePassRate - baselinePassRate */
    private Double passRateDelta;
}
