package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 知识库详情 DTO。
 */
@Data
@Builder
public class KbDetailDTO {

    private String kbNum;
    private String workspaceNum;
    private String name;
    private String description;
    private String kbType;
    private String status;
    private KbIndexConfigDTO indexConfig;
    private KbSourceConfigDTO sourceConfig;
    private KbRetrievalDefaultsDTO retrievalDefaults;
    private Integer fileCount;
    private Integer chunkCount;
    private Integer agentsBoundCount;
    private List<KbConfigAlignmentDTO> configAlignment;
}
