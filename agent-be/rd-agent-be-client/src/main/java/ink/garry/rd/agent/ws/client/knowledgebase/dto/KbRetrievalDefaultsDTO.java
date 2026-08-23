package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库默认检索参数 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbRetrievalDefaultsDTO {

    private Integer topK;
    private Double minScore;
}
