package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 更新评估器。 */
@Data
public class UpdateGraderParam {
    @NotBlank
    private String num;
    private String name;
    private String description;
    private String configJson;
}
