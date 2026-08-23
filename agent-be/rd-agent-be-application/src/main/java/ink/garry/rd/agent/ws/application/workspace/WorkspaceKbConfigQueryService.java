package ink.garry.rd.agent.ws.application.workspace;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceKbConfigDTO;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceKbConfigRepository;
import ink.garry.rd.agent.ws.domain.workspace.valueobject.WorkspaceKbConfig;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkspaceKbConfigQueryService {

    @Resource
    private WorkspaceKbConfigRepository workspaceKbConfigRepository;
    @Resource
    private KnowledgeBaseProperties knowledgeBaseProperties;

    public WorkspaceKbConfigDTO get(String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        WorkspaceKbConfig config = workspaceKbConfigRepository.findByWorkspaceNum(workspaceNum);
        if (config == null) {
            config = WorkspaceKbConfig.builder()
                    .workspaceNum(workspaceNum)
                    .allowedKbTypes(List.of("SIMPLE", "RAG_FLOW"))
                    .defaultEmbeddingModelId(knowledgeBaseProperties.getDefaultEmbeddingModelId())
                    .defaultChunkSize(512)
                    .defaultChunkOverlap(64)
                    .defaultSplitStrategy("PARAGRAPH")
                    .maxFileSizeMb(knowledgeBaseProperties.getIndexing().getMaxFileSizeMb())
                    .build();
        }
        return toDTO(config, knowledgeBaseProperties);
    }

    static WorkspaceKbConfigDTO toDTO(WorkspaceKbConfig config, KnowledgeBaseProperties props) {
        WorkspaceKbConfigDTO dto = new WorkspaceKbConfigDTO();
        dto.setWorkspaceNum(config.getWorkspaceNum());
        dto.setAllowedKbTypes(config.getAllowedKbTypes());
        dto.setDefaultEmbeddingModelId(StrUtilBlank(config.getDefaultEmbeddingModelId(), props.getDefaultEmbeddingModelId()));
        dto.setDefaultChunkSize(config.getDefaultChunkSize());
        dto.setDefaultChunkOverlap(config.getDefaultChunkOverlap());
        dto.setDefaultSplitStrategy(config.getDefaultSplitStrategy());
        dto.setWordSeparateTables(config.getWordSeparateTables());
        dto.setWordTableFormat(config.getWordTableFormat());
        dto.setRagFlowEndpoint(config.getRagFlowEndpoint());
        dto.setRagFlowApiKeyMasked(mask(config.getRagFlowApiKeyEncrypted()));
        dto.setAllowedMimeTypes(config.getAllowedMimeTypes());
        dto.setMaxFileSizeMb(config.getMaxFileSizeMb());
        return dto;
    }

    private static String StrUtilBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "***";
    }
}
