package ink.garry.rd.agent.ws.client.evaluation.grader;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评估器分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GraderPageQuery extends PageParam {
    private String keyword;
    private String kind;
}
