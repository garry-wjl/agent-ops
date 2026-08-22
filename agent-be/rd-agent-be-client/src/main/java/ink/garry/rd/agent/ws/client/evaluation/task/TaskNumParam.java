package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 任务单编号操作。 */
@Data
public class TaskNumParam {
    @NotBlank
    private String num;
}
