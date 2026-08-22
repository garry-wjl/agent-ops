package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发布门禁预检参数。 */
@Data
public class PublishGateCheckParam {
    @NotBlank
    private String agentNum;
    @NotBlank
    private String agentVersionNum;
}
