package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 知识库绑定 DTO。
 */
@Data
@Builder
public class KnowledgeBaseBindingDTO {

    private String kbNum;
    private String kbName;
    private String retrievalMode;
    private Integer topK;
    private Double minScore;
}
