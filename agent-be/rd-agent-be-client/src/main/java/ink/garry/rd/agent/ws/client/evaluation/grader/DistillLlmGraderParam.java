package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 从评测任务标注蒸馏 LLM 评估器。 */
@Data
public class DistillLlmGraderParam {
    @NotBlank
    private String taskNum;
    @NotBlank
    private String name;
    @NotBlank
    private String modelNum;
    private String description;
}
