package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.Data;

/** 评测集行 VO。 */
@Data
public class EvalDatasetRowVO {
    private String num;
    private Integer rowIndex;
    private Integer version;
    private String dataJson;
}
