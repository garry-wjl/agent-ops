package ink.garry.rd.agent.ws.client.evaluation.dataset;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 自动生成任务分页（按评测集）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CaseGenJobPageQuery extends PageParam {
    private String datasetNum;
    private String status;
}
