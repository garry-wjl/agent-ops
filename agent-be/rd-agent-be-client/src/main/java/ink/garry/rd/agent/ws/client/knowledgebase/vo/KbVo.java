package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

@Data
public class KbVo {
    private String kbNum;
    private String workspaceNum;
    private String name;
    private String description;
    private String kbType;
    private String status;
    private Integer fileCount;
    private Integer chunkCount;
    private Integer configVersion;
    private Integer agentsBoundCount;
}
