package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Agent 知识库绑定入参。
 */
@Data
public class KnowledgeBaseBindingParam {

    private String kbNum;
    private String retrievalMode;
    private Integer topK;
    private Double minScore;
}
