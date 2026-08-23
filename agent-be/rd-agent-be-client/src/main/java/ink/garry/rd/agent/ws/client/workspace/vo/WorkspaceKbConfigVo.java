package ink.garry.rd.agent.ws.client.workspace.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkspaceKbConfigVo {
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
