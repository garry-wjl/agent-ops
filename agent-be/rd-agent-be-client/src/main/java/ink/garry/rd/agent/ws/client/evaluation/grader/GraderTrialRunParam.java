package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/** 评估器试跑入参。 */
@Data
public class GraderTrialRunParam {
    @NotBlank
    private String graderNum;
    @NotNull
    private Map<String, Object> variables;
}
