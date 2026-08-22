package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 任务对比入参。 */
@Data
public class TaskCompareParam {
    @NotBlank
    private String leftTaskNum;
    @NotBlank
    private String rightTaskNum;
}
