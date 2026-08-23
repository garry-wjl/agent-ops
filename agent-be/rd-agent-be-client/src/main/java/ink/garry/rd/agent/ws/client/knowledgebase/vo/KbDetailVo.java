package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbConfigAlignmentDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbIndexConfigDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbRetrievalDefaultsDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbSourceConfigDTO;
import lombok.Data;

import java.util.List;

@Data
public class KbDetailVo {
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
