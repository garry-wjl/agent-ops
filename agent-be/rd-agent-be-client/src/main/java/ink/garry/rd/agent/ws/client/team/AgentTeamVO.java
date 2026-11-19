package ink.garry.rd.agent.ws.client.team;

import lombok.Data;

/**
 * Agent Team 列表与详情。definitionJson 是画布或 Leader/Worker。
 */
@Data
public class AgentTeamVO {
    private String num;
    private String name;
    private String description;
    private String mode;
    private String status;
    private String definitionJson;
    private String updatedAt;
}
