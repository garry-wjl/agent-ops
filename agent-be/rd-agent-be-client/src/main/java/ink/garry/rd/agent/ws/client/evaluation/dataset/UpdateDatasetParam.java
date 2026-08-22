package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 更新评测集草稿入参。 */
@Data
public class UpdateDatasetParam {
    @NotBlank
    private String num;
    private String name;
    private String description;
    private String schemaJson;
}
