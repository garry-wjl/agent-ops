package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 评测集详情 VO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvalDatasetDetailVO extends EvalDatasetVO {
    /** schema JSON */
    private String schemaJson;
    /** 已发布版本列表 */
    private List<EvalDatasetVersionVO> versions;
}
