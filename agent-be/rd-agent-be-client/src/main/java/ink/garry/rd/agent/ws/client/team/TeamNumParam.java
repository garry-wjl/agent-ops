package ink.garry.rd.agent.ws.client.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 按编号操作团队或运行。 */
@Data
public class TeamNumParam {
    @NotBlank
    private String num;
}
