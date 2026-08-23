package ink.garry.rd.agent.ws.domain.workspace.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 工作空间知识库默认配置值对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceKbConfig {

    private String workspaceNum;
    private List<String> allowedKbTypes;
    private String defaultEmbeddingModelId;
    private Integer defaultChunkSize;
    private Integer defaultChunkOverlap;
    private String defaultSplitStrategy;
    private Boolean wordSeparateTables;
    private String wordTableFormat;
    private String ragFlowEndpoint;
    private String ragFlowApiKeyEncrypted;
    private List<String> allowedMimeTypes;
    private Integer maxFileSizeMb;
}
