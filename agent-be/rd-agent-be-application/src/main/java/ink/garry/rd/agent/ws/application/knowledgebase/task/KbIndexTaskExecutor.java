package ink.garry.rd.agent.ws.application.knowledgebase.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.attachment.gateway.OssObjectGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.factory.KnowledgeBaseFactory;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KbIndexTaskRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.IndexResult;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.knowledgebase.support.KnowledgeBaseRagGatewayRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class KbIndexTaskExecutor {

    @Resource
    private KbIndexTaskRepository kbIndexTaskRepository;
    @Resource
    private KnowledgeBaseFactory knowledgeBaseFactory;
    @Resource
    private KnowledgeBaseFileRepository knowledgeBaseFileRepository;
    @Resource
    private KnowledgeBaseRagGatewayRegistry ragGatewayRegistry;
    @Resource
    private OssObjectGateway ossObjectGateway;
    @Resource
    private KbNumGateway kbNumGateway;
    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public void execute(String taskNum) {
        KbIndexTask task = kbIndexTaskRepository.findByNum(taskNum);
        if (task == null) {
            return;
        }
        RLock lock = redissonClient.getLock(LockKeyConstant.KB_INDEX_TASK_LOCK_PREFIX + taskNum);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return;
            }
            if (!KbIndexTaskStatus.PENDING.name().equals(task.getStatus())) {
                return;
            }
            task.setStatus(KbIndexTaskStatus.RUNNING.name());
            task.setUpdateTime(LocalDateTime.now());
            kbIndexTaskRepository.save(task);

            KbIndexTaskType type = KbIndexTaskType.valueOf(task.getTaskType());
            if (type == KbIndexTaskType.REINDEX_ALL) {
                fanOutReindexAll(task);
                markSuccess(task, null);
                return;
            }
            executeFileTask(task, type);
        } catch (Exception ex) {
            markFailed(task, ex.getMessage());
            log.warn("KbIndexTask failed taskNum={}: {}", taskNum, ex.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void fanOutReindexAll(KbIndexTask parent) {
        List<KnowledgeBaseFile> files = knowledgeBaseFileRepository.listByKbNum(parent.getKbNum());
        for (KnowledgeBaseFile file : files) {
            KbIndexTask child = new KbIndexTask();
            child.setNum(kbNumGateway.generateTaskNum());
            child.setKbNum(parent.getKbNum());
            child.setFileNum(file.getNum());
            child.setTaskType(KbIndexTaskType.REINDEX_FILE.name());
            child.setStatus(KbIndexTaskStatus.PENDING.name());
            child.setRetryCount(0);
            child.setMaxRetries(3);
            child.setCreateNo(parent.getCreateNo());
            child.setUpdateNo(parent.getUpdateNo());
            child.setCreateTime(LocalDateTime.now());
            child.setUpdateTime(LocalDateTime.now());
            kbIndexTaskRepository.save(child);
        }
    }

    private void executeFileTask(KbIndexTask task, KbIndexTaskType type) {
        KnowledgeBase kb = knowledgeBaseFactory.requireByNum(task.getKbNum());
        KnowledgeBaseFile file = knowledgeBaseFactory.createFileByNum(task.getFileNum());
        if (file == null) {
            throw new BusinessException(BizCode.KBF_FILE_NOT_FOUND.getCode(), "文件不存在");
        }
        file.setKnowledgeBaseFileRepository(knowledgeBaseFileRepository);
        file.markIndexing(task.getUpdateNo());
        if (type == KbIndexTaskType.REINDEX_FILE) {
            ragGatewayRegistry.get(kb.getKbType()).deleteFileVectors(kb, file);
        }
        byte[] bytes = ossObjectGateway.downloadBytes(file.getOssFileId());
        IndexResult result = ragGatewayRegistry.get(kb.getKbType()).indexFile(
                kb, file, new ByteArrayInputStream(bytes), kb.getIndexConfig());
        file.markReady(kb.getIndexConfig(), result.getChunkCount(), result.getVectorDocIds(), task.getUpdateNo());
        kb.refreshAggregateStatus(task.getUpdateNo());
        markSuccess(task, null);
    }

    private void markSuccess(KbIndexTask task, String message) {
        task.setStatus(KbIndexTaskStatus.SUCCESS.name());
        task.setErrorMessage(message);
        task.setUpdateTime(LocalDateTime.now());
        kbIndexTaskRepository.save(task);
    }

    private void markFailed(KbIndexTask task, String message) {
        if (task == null) {
            return;
        }
        task.setRetryCount(task.getRetryCount() + 1);
        if (task.getRetryCount() < task.getMaxRetries()) {
            task.setStatus(KbIndexTaskStatus.PENDING.name());
        } else {
            task.setStatus(KbIndexTaskStatus.FAILED.name());
        }
        task.setErrorMessage(StrUtil.sub(message, 0, 1000));
        task.setUpdateTime(LocalDateTime.now());
        kbIndexTaskRepository.save(task);
        if (KbIndexTaskStatus.FAILED.name().equals(task.getStatus()) && StrUtil.isNotBlank(task.getFileNum())) {
            KnowledgeBaseFile file = knowledgeBaseFactory.createFileByNum(task.getFileNum());
            if (file != null) {
                file.setKnowledgeBaseFileRepository(knowledgeBaseFileRepository);
                file.markFailed(message, task.getUpdateNo());
                KnowledgeBase kb = knowledgeBaseFactory.requireByNum(task.getKbNum());
                kb.refreshAggregateStatus(task.getUpdateNo());
            }
        }
    }
}
