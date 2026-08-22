package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 重试失败的自动生成任务。 */
@Data
public class RetryCaseGenParam {
    @NotBlank
    private String jobNum;
}
