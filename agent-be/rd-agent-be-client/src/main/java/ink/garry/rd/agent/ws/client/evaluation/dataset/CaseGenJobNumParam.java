package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 查询自动生成任务详情。 */
@Data
public class CaseGenJobNumParam {
    @NotBlank
    private String jobNum;
}
