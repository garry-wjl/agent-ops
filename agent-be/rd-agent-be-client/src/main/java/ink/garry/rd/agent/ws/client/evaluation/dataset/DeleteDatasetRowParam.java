package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 删除评测集草稿行。 */
@Data
public class DeleteDatasetRowParam {
    /** 评测集编号 */
    @NotBlank
    private String datasetNum;
    /** 行业务编号（EDR） */
    @NotBlank
    private String rowNum;
}
