package ink.garry.rd.agent.ws.application.knowledgebase;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbChunkDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbConfigAlignmentDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDetailDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbFileDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbIndexConfigDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbListParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbRetrievalDefaultsDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbSourceConfigDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbTestRetrieveParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbTypeSchemaDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.MountableKbItemDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseFileEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseFileMapper;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseMapper;
import ink.garry.rd.agent.ws.infra.knowledgebase.support.KnowledgeBaseRagGatewayRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Resource
    private KnowledgeBaseFileMapper knowledgeBaseFileMapper;
    @Resource
    private AgentMapper agentMapper;
    @Resource
    private KnowledgeBaseRagGatewayRegistry ragGatewayRegistry;

    public PageVO<KbDTO> list(KbListParamDTO param, String workspaceNum) {
        requireWorkspace(workspaceNum);
        int pageNo = param.getPageNo() == null || param.getPageNo() < 1 ? 1 : param.getPageNo();
        int pageSize = normalizePageSize(param.getPageSize());
        Page<KnowledgeBaseEntity> page = Page.of(pageNo, pageSize);
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getWorkspaceNum, workspaceNum)
                .like(StrUtil.isNotBlank(param.getKeyword()), KnowledgeBaseEntity::getName, param.getKeyword())
                .eq(StrUtil.isNotBlank(param.getKbType()), KnowledgeBaseEntity::getKbType, param.getKbType())
                .eq(StrUtil.isNotBlank(param.getStatus()), KnowledgeBaseEntity::getStatus, param.getStatus())
                .orderByDesc(KnowledgeBaseEntity::getUpdateTime);
        Page<KnowledgeBaseEntity> result = knowledgeBaseMapper.selectPage(page, wrapper);
        Map<String, Integer> boundMap = buildBoundCountMap();
        List<KbDTO> list = result.getRecords().stream()
                .map(e -> toDTO(KnowledgeBaseEntity.toDomain(e), boundMap.getOrDefault(e.getNum(), 0)))
                .collect(Collectors.toList());
        return PageVO.of(list, result.getTotal(), pageNo, pageSize);
    }

    public KbDetailDTO detail(String kbNum, String workspaceNum) {
        KnowledgeBaseEntity entity = requireEntity(kbNum, workspaceNum);
        KnowledgeBase kb = KnowledgeBaseEntity.toDomain(entity);
        return KbDetailDTO.builder()
                .kbNum(kb.getNum())
                .workspaceNum(kb.getWorkspaceNum())
                .name(kb.getName())
                .description(kb.getDescription())
                .kbType(kb.getKbType().name())
                .status(kb.getStatus().name())
                .indexConfig(toIndexConfigDTO(kb.getIndexConfig()))
                .sourceConfig(toSourceConfigDTO(kb.getSourceConfig()))
                .retrievalDefaults(toRetrievalDefaultsDTO(kb.getRetrievalDefaults()))
                .fileCount(kb.getFileCount())
                .chunkCount(kb.getChunkCount())
                .agentsBoundCount(countAgentsBound(kbNum))
                .configAlignment(configAlignmentStats(kbNum))
                .build();
    }

    public List<KbFileDTO> listFiles(String kbNum) {
        requireEntity(kbNum, null);
        return knowledgeBaseFileMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                        .eq(KnowledgeBaseFileEntity::getKbNum, kbNum)
                        .orderByDesc(KnowledgeBaseFileEntity::getUpdateTime))
                .stream()
                .map(this::toFileDTO)
                .collect(Collectors.toList());
    }

    public List<MountableKbItemDTO> mountable(String workspaceNum) {
        requireWorkspace(workspaceNum);
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .eq(KnowledgeBaseEntity::getWorkspaceNum, workspaceNum)
                        .eq(KnowledgeBaseEntity::getStatus, KbStatus.READY.name())
                        .orderByDesc(KnowledgeBaseEntity::getUpdateTime))
                .stream()
                .map(e -> MountableKbItemDTO.builder()
                        .kbNum(e.getNum())
                        .name(e.getName())
                        .description(e.getDescription())
                        .kbType(e.getKbType())
                        .status(e.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    public List<KbTypeSchemaDTO> typeSchemas() {
        List<KbTypeSchemaDTO> schemas = new ArrayList<>();
        for (KbType type : KbType.values()) {
            KbTypeSchemaDTO dto = new KbTypeSchemaDTO();
            dto.setKbType(type.name());
            dto.setFields(List.of(Map.of("name", "embeddingModelId", "required", type == KbType.SIMPLE)));
            schemas.add(dto);
        }
        return schemas;
    }

    public int countAgentsBound(String kbNum) {
        return buildBoundCountMap().getOrDefault(kbNum, 0);
    }

    public int countIndexedFiles(String kbNum) {
        Long count = knowledgeBaseFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                .eq(KnowledgeBaseFileEntity::getKbNum, kbNum)
                .eq(KnowledgeBaseFileEntity::getIndexStatus, KbIndexStatus.READY.name()));
        return count == null ? 0 : count.intValue();
    }

    public List<KbConfigAlignmentDTO> configAlignmentStats(String kbNum) {
        KnowledgeBaseEntity kb = requireEntity(kbNum, null);
        int current = JSON.parseObject(kb.getIndexConfig()).getIntValue("configVersion");
        Map<Integer, Long> grouped = knowledgeBaseFileMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseFileEntity>()
                        .eq(KnowledgeBaseFileEntity::getKbNum, kbNum))
                .stream()
                .collect(Collectors.groupingBy(f -> {
                    if (f.getIndexConfigSnapshot() == null) {
                        return 0;
                    }
                    return JSON.parseObject(f.getIndexConfigSnapshot()).getIntValue("configVersion");
                }, Collectors.counting()));
        List<KbConfigAlignmentDTO> result = new ArrayList<>();
        grouped.forEach((version, count) -> result.add(KbConfigAlignmentDTO.builder()
                .configVersion(version)
                .fileCount(count.intValue())
                .current(version == current)
                .build()));
        return result;
    }

    public List<KbChunkDTO> testRetrieve(KbTestRetrieveParamDTO param) {
        KnowledgeBase kb = KnowledgeBaseEntity.toDomain(requireEntity(param.getKbNum(), null));
        int topK = param.getTopK() == null ? 5 : param.getTopK();
        double minScore = param.getMinScore() == null ? 0.0 : param.getMinScore();
        List<RetrievedChunk> chunks = ragGatewayRegistry.get(kb.getKbType())
                .retrieve(kb, param.getQuestion(), topK, minScore);
        return chunks.stream().map(c -> KbChunkDTO.builder()
                .kbNum(c.getKbNum())
                .fileNum(c.getFileNum())
                .fileName(c.getFileName())
                .configVersion(c.getConfigVersion())
                .content(c.getContent())
                .score(c.getScore())
                .build()).collect(Collectors.toList());
    }

    public boolean existsByName(String workspaceNum, String name, String excludeNum) {
        Long count = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getWorkspaceNum, workspaceNum)
                .eq(KnowledgeBaseEntity::getName, name)
                .ne(StrUtil.isNotBlank(excludeNum), KnowledgeBaseEntity::getNum, excludeNum));
        return count != null && count > 0;
    }

    public boolean isEmbeddingDimensionCompatible(KnowledgeBase kb, ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig newConfig) {
        if (kb.getKbType() != KbType.SIMPLE) {
            return true;
        }
        String oldModel = kb.getIndexConfig() == null ? null : kb.getIndexConfig().getEmbeddingModelId();
        String newModel = newConfig == null ? null : newConfig.getEmbeddingModelId();
        return StrUtil.equals(oldModel, newModel);
    }

    public static KbDTO toDTO(KnowledgeBase kb, int agentsBound) {
        return KbDTO.builder()
                .kbNum(kb.getNum())
                .workspaceNum(kb.getWorkspaceNum())
                .name(kb.getName())
                .description(kb.getDescription())
                .kbType(kb.getKbType().name())
                .status(kb.getStatus().name())
                .fileCount(kb.getFileCount())
                .chunkCount(kb.getChunkCount())
                .configVersion(kb.getIndexConfig() == null ? null : kb.getIndexConfig().getConfigVersion())
                .agentsBoundCount(agentsBound)
                .build();
    }

    private KnowledgeBaseEntity requireEntity(String kbNum, String workspaceNum) {
        Assert.notBlank(kbNum, "kbNum 不能为空");
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getNum, kbNum));
        if (entity == null) {
            throw new BusinessException(BizCode.KB_NOT_FOUND.getCode(), "知识库不存在");
        }
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该知识库");
        }
        return entity;
    }

    private KbFileDTO toFileDTO(KnowledgeBaseFileEntity e) {
        Integer version = e.getIndexConfigSnapshot() == null ? null
                : JSON.parseObject(e.getIndexConfigSnapshot()).getInteger("configVersion");
        return KbFileDTO.builder()
                .fileNum(e.getNum())
                .kbNum(e.getKbNum())
                .ossFileId(e.getOssFileId())
                .fileName(e.getFileName())
                .mimeType(e.getMimeType())
                .fileSize(e.getFileSize())
                .indexStatus(e.getIndexStatus())
                .indexConfigVersion(version)
                .chunkCount(e.getChunkCount())
                .errorMessage(e.getErrorMessage())
                .indexedAt(e.getIndexedAt())
                .build();
    }

    private static KbIndexConfigDTO toIndexConfigDTO(ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return KbIndexConfigDTO.builder()
                .configVersion(cfg.getConfigVersion())
                .embeddingModelId(cfg.getEmbeddingModelId())
                .splitStrategy(cfg.getSplitStrategy() == null ? null : cfg.getSplitStrategy().name())
                .chunkSize(cfg.getChunkSize())
                .chunkOverlap(cfg.getChunkOverlap())
                .wordSeparateTables(cfg.getWordSeparateTables())
                .wordTableFormat(cfg.getWordTableFormat())
                .parserId(cfg.getParserId())
                .chunkMethod(cfg.getChunkMethod())
                .layoutRecognize(cfg.getLayoutRecognize())
                .build();
    }

    private static KbSourceConfigDTO toSourceConfigDTO(ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return KbSourceConfigDTO.builder()
                .vectorCollectionName(cfg.getVectorCollectionName())
                .dimensions(cfg.getDimensions())
                .ragFlowDatasetId(cfg.getRagFlowDatasetId())
                .build();
    }

    private static KbRetrievalDefaultsDTO toRetrievalDefaultsDTO(
            ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults cfg) {
        if (cfg == null) {
            return null;
        }
        return KbRetrievalDefaultsDTO.builder().topK(cfg.getTopK()).minScore(cfg.getMinScore()).build();
    }

    private Map<String, Integer> buildBoundCountMap() {
        Map<String, Integer> counter = new HashMap<>();
        List<AgentEntity> agents = agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getStatus, AgentStatus.PUBLISHED.name()));
        if (agents == null) {
            return counter;
        }
        for (AgentEntity agent : agents) {
            for (String kbNum : kbNumsOf(agent)) {
                counter.merge(kbNum, 1, Integer::sum);
            }
        }
        return counter;
    }

    private List<String> kbNumsOf(AgentEntity agent) {
        if (StrUtil.isBlank(agent.getConfigSnapshot())) {
            return List.of();
        }
        ConfigSnapshot snapshot = JSON.parseObject(agent.getConfigSnapshot(), ConfigSnapshot.class);
        if (snapshot == null || snapshot.getKnowledgeBaseBindings() == null) {
            return List.of();
        }
        return snapshot.getKnowledgeBaseBindings().stream()
                .map(KnowledgeBaseBinding::getKbNum)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static void requireWorkspace(String workspaceNum) {
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "未指定工作空间");
        }
    }
}
