package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 发布门禁预检参数。 */
@Data
public class PublishGateCheckParam {
    @NotBlank
    private String agentNum;
    /** 当前在线版本号；首次发布可空（门禁关闭或首次发布会放行）。 */
    private String agentVersionNum;
}
