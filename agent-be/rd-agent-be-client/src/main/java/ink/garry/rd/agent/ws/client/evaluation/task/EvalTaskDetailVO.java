package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评测任务详情。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvalTaskDetailVO extends EvalTaskVO {
    private String graderBindingsJson;
    private String labelConfigJson;
}
