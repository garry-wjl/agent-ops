package ink.garry.rd.agent.ws.client.team;

import lombok.Data;

/**
 * 一次运行。ossSubPath 是挂到每个 Agent 容器的 OSS 前缀。
 */
@Data
public class AgentTeamRunVO {
    private String num;
    private String teamNum;
    private String teamName;
    private String mode;
    private String input;
    private String status;
    private String ossSubPath;
    private String snapshotJson;
    private String runtimeJson;
    private String createdAt;
}
