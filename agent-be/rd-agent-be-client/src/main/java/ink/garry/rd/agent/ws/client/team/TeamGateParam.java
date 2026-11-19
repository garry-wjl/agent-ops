package ink.garry.rd.agent.ws.client.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 人工闸门通过或打回。 */
@Data
public class TeamGateParam {
    @NotBlank
    private String runNum;
    @NotBlank
    private String nodeId;
    private String comment;
}
