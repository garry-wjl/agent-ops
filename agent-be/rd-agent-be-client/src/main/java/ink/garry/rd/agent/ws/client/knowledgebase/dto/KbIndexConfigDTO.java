package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库索引配置 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbIndexConfigDTO {

    private Integer configVersion;
    private String embeddingModelId;
    private String splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Boolean wordSeparateTables;
    private String wordTableFormat;
    private String parserId;
    private String chunkMethod;
    private Boolean layoutRecognize;
}
