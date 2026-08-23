package ink.garry.rd.agent.ws.domain.knowledgebase.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 知识库默认检索参数。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbRetrievalDefaults {

    private Integer topK;
    private Double minScore;
}
