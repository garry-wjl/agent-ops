package ink.garry.rd.agent.ws.client.team;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 当前工作空间的团队和运行。 */
@Data
public class AgentTeamBucketVO {
    private List<AgentTeamVO> teams = new ArrayList<>();
    private List<AgentTeamRunVO> runs = new ArrayList<>();
}
