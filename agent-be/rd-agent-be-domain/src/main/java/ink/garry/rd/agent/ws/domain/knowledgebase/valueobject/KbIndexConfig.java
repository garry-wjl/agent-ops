package ink.garry.rd.agent.ws.domain.knowledgebase.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 知识库索引配置值对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbIndexConfig {

    private int configVersion;
    private String embeddingModelId;
    private SplitStrategy splitStrategy;
    private int chunkSize;
    private int chunkOverlap;
    private Boolean wordSeparateTables;
    private String wordTableFormat;
    private String parserId;
    private String chunkMethod;
    private Boolean layoutRecognize;
}
