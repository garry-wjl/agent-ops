package ink.garry.rd.agent.ws.client.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 启动一次运行。草稿也可以预览。 */
@Data
public class TeamRunParam {
    @NotBlank
    private String teamNum;
    @NotBlank
    private String input;
}
