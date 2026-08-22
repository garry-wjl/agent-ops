package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 评估器单编号操作。 */
@Data
public class GraderNumParam {
    @NotBlank
    private String num;
}
