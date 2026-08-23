package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库列表项 DTO。
 */
@Data
@Builder
public class KbDTO {

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
