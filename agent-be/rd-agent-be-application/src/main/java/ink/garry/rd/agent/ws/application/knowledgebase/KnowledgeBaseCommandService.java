package ink.garry.rd.agent.ws.application.knowledgebase;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.knowledgebase.task.KbIndexTaskExecutor;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbCreateParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateBasicParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUpdateIndexConfigParamDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbUploadFileParamDTO;
import ink.garry.rd.agent.ws.domain.attachment.factory.ChatAttachmentFactory;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.factory.KnowledgeBaseFactory;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KbIndexTaskRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.SplitStrategy;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import ink.garry.rd.agent.ws.infra.knowledgebase.support.KnowledgeBaseRagGatewayRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class KnowledgeBaseCommandService {

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private KnowledgeBaseFactory knowledgeBaseFactory;
    @Resource
    private KnowledgeBaseQueryService knowledgeBaseQueryService;
    @Resource
    private KnowledgeBaseRagGatewayRegistry ragGatewayRegistry;
    @Resource
    private KbIndexTaskRepository kbIndexTaskRepository;
    @Resource
    private KbNumGateway kbNumGateway;
    @Resource
    private ChatAttachmentFactory chatAttachmentFactory;
    @Resource
    private KbIndexTaskExecutor kbIndexTaskExecutor;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private KnowledgeBaseProperties knowledgeBaseProperties;

    @Transactional(rollbackFor = Exception.class)
    public KbDTO create(KbCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "param 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        requireWorkspace(param.getWorkspaceNum());
        KbType kbType = parseKbType(param.getKbType());
        String lockKey = LockKeyConstant.KB_CREATE_LOCK_PREFIX + param.getWorkspaceNum() + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (knowledgeBaseQueryService.existsByName(param.getWorkspaceNum(), param.getName(), null)) {
                throw new BusinessException(BizCode.KB_NAME_DUPLICATE.getCode(), "同空间内知识库名称已存在");
            }
            KbIndexConfig indexConfig = toIndexConfig(param.getIndexConfig(), 1);
            KbRetrievalDefaults defaults = toRetrievalDefaults(param.getRetrievalDefaults());
            KnowledgeBase kb = knowledgeBaseFactory.create(
                    param.getWorkspaceNum(), param.getName(), param.getDescription(), kbType, indexConfig, defaults);
            kb.save(operatorId);
            var source = ragGatewayRegistry.get(kbType).provision(kb);
            kb.applyProvisioned(source, operatorId);
            return KnowledgeBaseQueryService.toDTO(kb, 0);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public KbDTO updateBasic(KbUpdateBasicParamDTO param, String operatorId) {
        KnowledgeBase kb = requireKb(param.getKbNum());
        return runWithLock(LockKeyConstant.KB_COMMAND_LOCK_PREFIX + kb.getNum(), () -> {
            if (knowledgeBaseQueryService.existsByName(kb.getWorkspaceNum(), param.getName(), kb.getNum())) {
                throw new BusinessException(BizCode.KB_NAME_DUPLICATE.getCode(), "同空间内知识库名称已存在");
            }
            kb.updateBasicInfo(param.getName(), param.getDescription(), operatorId);
            return KnowledgeBaseQueryService.toDTO(kb, knowledgeBaseQueryService.countAgentsBound(kb.getNum()));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public KbDTO updateIndexConfig(KbUpdateIndexConfigParamDTO param, String operatorId) {
        KnowledgeBase kb = requireKb(param.getKbNum());
        return runWithLock(LockKeyConstant.KB_COMMAND_LOCK_PREFIX + kb.getNum(), () -> {
            int indexed = knowledgeBaseQueryService.countIndexedFiles(kb.getNum());
            boolean dimensionCompatible = knowledgeBaseQueryService.isEmbeddingDimensionCompatible(
                    kb, toIndexConfig(param.getIndexConfig(), kb.getIndexConfig().getConfigVersion()));
            kb.updateIndexConfig(toIndexConfig(param.getIndexConfig(), kb.getIndexConfig().getConfigVersion() + 1),
                    indexed, dimensionCompatible, operatorId);
            return KnowledgeBaseQueryService.toDTO(kb, knowledgeBaseQueryService.countAgentsBound(kb.getNum()));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String kbNum, String operatorId) {
        KnowledgeBase kb = requireKb(kbNum);
        runWithLock(LockKeyConstant.KB_COMMAND_LOCK_PREFIX + kb.getNum(), () -> {
            if (knowledgeBaseQueryService.countAgentsBound(kbNum) > 0) {
                throw new BusinessException(BizCode.KB_BOUND_BY_AGENT.getCode(), "知识库已被 Agent 绑定，无法删除");
            }
            ragGatewayRegistry.get(kb.getKbType()).teardown(kb);
            kb.delete(operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public KbDTO registerUploadedFile(KbUploadFileParamDTO param, String workspaceNum, String operatorId) {
        KnowledgeBase kb = requireKb(param.getKbNum());
        if (!workspaceNum.equals(kb.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权操作该知识库");
        }
        validateOssOwnership(param.getOssFileId(), workspaceNum);
        return runWithLock(LockKeyConstant.KB_COMMAND_LOCK_PREFIX + kb.getNum(), () -> {
            KnowledgeBaseFile file = knowledgeBaseFactory.createFile(
                    param.getOssFileId(), param.getFileName(), param.getMimeType(), param.getFileSize());
            kb.registerFile(file, operatorId);
            createIndexTask(kb.getNum(), file.getNum(), KbIndexTaskType.INDEX_FILE, operatorId);
            return KnowledgeBaseQueryService.toDTO(kb, knowledgeBaseQueryService.countAgentsBound(kb.getNum()));
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String kbNum, String fileNum, String operatorId) {
        KnowledgeBase kb = requireKb(kbNum);
        KnowledgeBaseFile file = requireFile(fileNum);
        runWithLock(LockKeyConstant.KB_COMMAND_LOCK_PREFIX + kb.getNum(), () -> {
            ragGatewayRegistry.get(kb.getKbType()).deleteFileVectors(kb, file);
            file.markDeleted(operatorId);
            kb.refreshAggregateStatus(operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void reindexFile(String kbNum, String fileNum, String operatorId) {
        requireKb(kbNum);
        requireFile(fileNum);
        createIndexTask(kbNum, fileNum, KbIndexTaskType.REINDEX_FILE, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reindexStaleFiles(String kbNum, String operatorId) {
        KnowledgeBase kb = requireKb(kbNum);
        int current = kb.getIndexConfig().getConfigVersion();
        knowledgeBaseQueryService.listFiles(kbNum).stream()
                .filter(f -> f.getIndexConfigVersion() != null && f.getIndexConfigVersion() < current)
                .forEach(f -> createIndexTask(kbNum, f.getFileNum(), KbIndexTaskType.REINDEX_FILE, operatorId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reindexAll(String kbNum, String operatorId) {
        requireKb(kbNum);
        createIndexTask(kbNum, null, KbIndexTaskType.REINDEX_ALL, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIndexTask(String taskNum) {
        kbIndexTaskExecutor.execute(taskNum);
    }

    public void syncRagFlowFileStatuses() {
        // MVP stub: RAG Flow 轮询由后续 Job 扩展
        log.debug("syncRagFlowFileStatuses noop");
    }

    private void createIndexTask(String kbNum, String fileNum, KbIndexTaskType type, String operatorId) {
        KbIndexTask task = new KbIndexTask();
        task.setNum(kbNumGateway.generateTaskNum());
        task.setKbNum(kbNum);
        task.setFileNum(fileNum);
        task.setTaskType(type.name());
        task.setStatus(KbIndexTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetries(3);
        task.setCreateNo(operatorId);
        task.setUpdateNo(operatorId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        kbIndexTaskRepository.save(task);
    }

    private void validateOssOwnership(String ossFileId, String workspaceNum) {
        var attachment = chatAttachmentFactory.createByFileId(ossFileId);
        if (attachment == null || !workspaceNum.equals(attachment.getWorkspaceNum())) {
            throw new BusinessException(BizCode.KBF_OSS_FORBIDDEN.getCode(), "OSS 文件不属于当前工作空间");
        }
    }

    private KnowledgeBase requireKb(String kbNum) {
        KnowledgeBase kb = knowledgeBaseFactory.createByNum(kbNum);
        if (kb == null || (kb.getDeleted() != null && kb.getDeleted() == 1)) {
            throw new BusinessException(BizCode.KB_NOT_FOUND.getCode(), "知识库不存在");
        }
        return kb;
    }

    private KnowledgeBaseFile requireFile(String fileNum) {
        KnowledgeBaseFile file = knowledgeBaseFactory.createFileByNum(fileNum);
        if (file == null) {
            throw new BusinessException(BizCode.KBF_FILE_NOT_FOUND.getCode(), "知识库文件不存在");
        }
        return file;
    }

    private static KbIndexConfig toIndexConfig(ink.garry.rd.agent.ws.client.knowledgebase.dto.KbIndexConfigDTO dto, int version) {
        if (dto == null) {
            return KbIndexConfig.builder().configVersion(version).chunkSize(512).chunkOverlap(64)
                    .splitStrategy(SplitStrategy.PARAGRAPH).build();
        }
        return KbIndexConfig.builder()
                .configVersion(version)
                .embeddingModelId(dto.getEmbeddingModelId())
                .splitStrategy(dto.getSplitStrategy() == null ? SplitStrategy.PARAGRAPH : SplitStrategy.valueOf(dto.getSplitStrategy()))
                .chunkSize(dto.getChunkSize() == null ? 512 : dto.getChunkSize())
                .chunkOverlap(dto.getChunkOverlap() == null ? 64 : dto.getChunkOverlap())
                .wordSeparateTables(dto.getWordSeparateTables())
                .wordTableFormat(dto.getWordTableFormat())
                .parserId(dto.getParserId())
                .chunkMethod(dto.getChunkMethod())
                .layoutRecognize(dto.getLayoutRecognize())
                .build();
    }

    private static KbRetrievalDefaults toRetrievalDefaults(ink.garry.rd.agent.ws.client.knowledgebase.dto.KbRetrievalDefaultsDTO dto) {
        if (dto == null) {
            return KbRetrievalDefaults.builder().topK(5).minScore(0.5).build();
        }
        return KbRetrievalDefaults.builder().topK(dto.getTopK()).minScore(dto.getMinScore()).build();
    }

    private static KbType parseKbType(String raw) {
        try {
            return KbType.valueOf(raw);
        } catch (Exception e) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "kbType 非法");
        }
    }

    private static void requireWorkspace(String workspaceNum) {
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "未指定工作空间");
        }
    }

    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(LOCK_WAIT, LOCK_LEASE, TimeUnit.SECONDS)) {
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
