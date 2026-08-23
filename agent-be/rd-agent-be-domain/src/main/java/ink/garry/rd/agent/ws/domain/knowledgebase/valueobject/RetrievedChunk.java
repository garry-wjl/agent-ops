package ink.garry.rd.agent.ws.domain.knowledgebase.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 检索结果 chunk 值对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    private String kbNum;
    private String fileNum;
    private String fileName;
    private int configVersion;
    private String content;
    private double score;
}
