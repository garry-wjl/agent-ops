package ink.garry.rd.agent.ws.client.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 保存草稿。num 为空时新建。 */
@Data
public class TeamSaveParam {
    private String num;
    @NotBlank
    private String name;
    private String description;
    /** orchestration 或 leader */
    @NotBlank
    private String mode;
    /** 画布或 Leader/Worker JSON */
    @NotBlank
    private String definitionJson;
}
