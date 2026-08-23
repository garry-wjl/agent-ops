package ink.garry.rd.agent.ws.domain.agent.valueobject;

import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 知识库绑定项。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseBinding {

    private String kbNum;
    private RetrievalMode retrievalMode;
    private Integer topK;
    private Double minScore;
}
