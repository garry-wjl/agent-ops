package ink.garry.rd.agent.ws.client.evaluation;

import lombok.Data;

import java.util.List;

/**
 * 评测详情 VO。
 * <p>
 * 继承 {@link EvaluationVO} 的所有头部统计字段，并附带评测下的全部用例明细 {@link #cases}，
 * 供评测详情页一次性渲染头部 + 用例列表。
 */
@Data
public class EvaluationDetailVO extends EvaluationVO {
    /** 评测下全部用例的明细列表 */
    private List<EvalCaseVO> cases;
}
