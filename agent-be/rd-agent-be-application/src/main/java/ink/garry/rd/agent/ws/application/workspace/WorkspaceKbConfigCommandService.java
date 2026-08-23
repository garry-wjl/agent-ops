package ink.garry.rd.agent.ws.application.workspace;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceKbConfigDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceKbConfigSaveParamDTO;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceKbConfigRepository;
import ink.garry.rd.agent.ws.domain.workspace.valueobject.WorkspaceKbConfig;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class WorkspaceKbConfigCommandService {

    @Resource
    private WorkspaceKbConfigRepository workspaceKbConfigRepository;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private KnowledgeBaseProperties knowledgeBaseProperties;

    @Transactional(rollbackFor = Exception.class)
    public WorkspaceKbConfigDTO save(WorkspaceKbConfigSaveParamDTO param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        return runWithLock(LockKeyConstant.WORKSPACE_KB_CONFIG_LOCK_PREFIX + workspaceNum, () -> {
            WorkspaceKbConfig config = toDomain(param, workspaceNum);
            workspaceKbConfigRepository.save(config, operatorId);
            return WorkspaceKbConfigQueryService.toDTO(config, knowledgeBaseProperties);
        });
    }

    private static WorkspaceKbConfig toDomain(WorkspaceKbConfigSaveParamDTO param, String workspaceNum) {
        if (param.getAllowedKbTypes() != null && param.getAllowedKbTypes().isEmpty()) {
            throw new BusinessException(BizCode.WS_KB_CONFIG_INVALID.getCode(), "allowedKbTypes 不能为空");
        }
        return WorkspaceKbConfig.builder()
                .workspaceNum(workspaceNum)
                .allowedKbTypes(param.getAllowedKbTypes() == null ? List.of("SIMPLE", "RAG_FLOW") : param.getAllowedKbTypes())
                .defaultEmbeddingModelId(param.getDefaultEmbeddingModelId())
                .defaultChunkSize(param.getDefaultChunkSize() == null ? 512 : param.getDefaultChunkSize())
                .defaultChunkOverlap(param.getDefaultChunkOverlap() == null ? 64 : param.getDefaultChunkOverlap())
                .defaultSplitStrategy(param.getDefaultSplitStrategy() == null ? "PARAGRAPH" : param.getDefaultSplitStrategy())
                .wordSeparateTables(param.getWordSeparateTables())
                .wordTableFormat(param.getWordTableFormat())
                .ragFlowEndpoint(param.getRagFlowEndpoint())
                .ragFlowApiKeyEncrypted(param.getRagFlowApiKey())
                .allowedMimeTypes(param.getAllowedMimeTypes())
                .maxFileSizeMb(param.getMaxFileSizeMb())
                .build();
    }

    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "操作冲突，请稍后重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
