package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 评测集单编号操作入参。 */
@Data
public class DatasetNumParam {
    @NotBlank
    private String num;
}
