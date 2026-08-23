package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建知识库入参 DTO。
 */
@Data
public class KbCreateParamDTO {

    private String workspaceNum;
    private String name;
    private String description;
    private String kbType;
    private KbIndexConfigDTO indexConfig;
    private KbRetrievalDefaultsDTO retrievalDefaults;
}
