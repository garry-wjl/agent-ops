package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.Data;

import java.util.List;

/**
 * 工作空间知识库配置 DTO。
 */
@Data
public class WorkspaceKbConfigDTO {

    private String workspaceNum;
    private List<String> allowedKbTypes;
    private String defaultEmbeddingModelId;
    private Integer defaultChunkSize;
    private Integer defaultChunkOverlap;
    private String defaultSplitStrategy;
    private Boolean wordSeparateTables;
    private String wordTableFormat;
    private String ragFlowEndpoint;
    private String ragFlowApiKeyMasked;
    private List<String> allowedMimeTypes;
    private Integer maxFileSizeMb;
}
