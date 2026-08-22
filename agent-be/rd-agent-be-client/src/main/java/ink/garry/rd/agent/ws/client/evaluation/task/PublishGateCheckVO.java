package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

/** Agent 发布门禁预检结果。 */
@Data
public class PublishGateCheckVO {
    private boolean enabled;
    private boolean passed;
    private String message;
    private Double passRate;
    private Double requiredPassRate;
    private Integer finishedTaskCount;
}
