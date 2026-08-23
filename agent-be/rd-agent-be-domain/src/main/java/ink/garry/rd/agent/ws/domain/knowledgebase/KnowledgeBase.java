package ink.garry.rd.agent.ws.domain.knowledgebase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 知识库聚合根。
 */
@Getter
@Setter
public class KnowledgeBase extends DomainEntity {

    private String num;
    private String workspaceNum;
    private String name;
    private String description;
    private KbType kbType;
    private KbStatus status;
    private KbIndexConfig indexConfig;
    private KbSourceConfig sourceConfig;
    private KbRetrievalDefaults retrievalDefaults;
    private int fileCount;
    private int chunkCount;

    private transient KnowledgeBaseRepository knowledgeBaseRepository;
    private transient KnowledgeBaseFileRepository knowledgeBaseFileRepository;
    private transient KbNumGateway kbNumGateway;

    public KnowledgeBase() {
    }

    public KnowledgeBase(String workspaceNum,
                         String name,
                         String description,
                         KbType kbType,
                         KbIndexConfig indexConfig,
                         KbRetrievalDefaults retrievalDefaults,
                         KnowledgeBaseRepository knowledgeBaseRepository,
                         KnowledgeBaseFileRepository knowledgeBaseFileRepository,
                         KbNumGateway kbNumGateway) {
        this.workspaceNum = workspaceNum;
        this.name = name;
        this.description = description;
        this.kbType = kbType;
        this.indexConfig = indexConfig;
        this.retrievalDefaults = retrievalDefaults;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseFileRepository = knowledgeBaseFileRepository;
        this.kbNumGateway = kbNumGateway;
        this.status = KbStatus.INDEXING;
        this.fileCount = 0;
        this.chunkCount = 0;
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "知识库名称不能为空");
        Assert.notNull(kbType, "kbType 不能为空");
        Assert.notNull(status, "status 不能为空");
        Assert.notNull(indexConfig, "indexConfig 不能为空");
    }

    @Override
    public void save(String operatorId) {
        this.initialize(operatorId);
        if (StrUtil.isBlank(num)) {
            num = kbNumGateway.generateKbNum();
        }
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void applyProvisioned(KbSourceConfig sourceConfig, String operatorId) {
        this.initialize(operatorId);
        this.sourceConfig = sourceConfig;
        this.status = KbStatus.READY;
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void updateBasicInfo(String name, String description, String operatorId) {
        this.initialize(operatorId);
        this.name = name;
        this.description = description;
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void updateIndexConfig(KbIndexConfig newConfig,
                                  int indexedFileCount,
                                  boolean dimensionCompatible,
                                  String operatorId) {
        this.initialize(operatorId);
        if (kbType == KbType.SIMPLE && indexedFileCount > 0 && !dimensionCompatible) {
            throw new BusinessException(1205, "存在已索引文件时不可变更 Embedding 维度");
        }
        int nextVersion = indexConfig == null ? 1 : indexConfig.getConfigVersion() + 1;
        newConfig.setConfigVersion(nextVersion);
        this.indexConfig = newConfig;
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void updateRetrievalDefaults(KbRetrievalDefaults defaults, String operatorId) {
        this.initialize(operatorId);
        this.retrievalDefaults = defaults;
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void registerFile(KnowledgeBaseFile file, String operatorId) {
        this.initialize(operatorId);
        if (StrUtil.isBlank(file.getNum())) {
            file.setNum(kbNumGateway.generateFileNum());
        }
        file.setKbNum(num);
        if (file.getIndexStatus() == null) {
            file.setIndexStatus(KbIndexStatus.PENDING);
        }
        file.initialize(operatorId);
        knowledgeBaseFileRepository.save(file);
        this.fileCount = knowledgeBaseFileRepository.countByKbNum(num);
        this.status = KbStatus.INDEXING;
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void refreshAggregateStatus(String operatorId) {
        this.initialize(operatorId);
        List<KnowledgeBaseFile> files = knowledgeBaseFileRepository.listByKbNum(num);
        if (CollUtil.isEmpty(files)) {
            this.status = KbStatus.READY;
            this.fileCount = 0;
            this.chunkCount = 0;
        } else {
            this.fileCount = files.size();
            this.chunkCount = files.stream().mapToInt(f -> f.getChunkCount() == null ? 0 : f.getChunkCount()).sum();
            boolean anyIndexing = files.stream().anyMatch(f ->
                    f.getIndexStatus() != KbIndexStatus.READY && f.getIndexStatus() != KbIndexStatus.FAILED);
            boolean anyFailed = files.stream().anyMatch(f -> f.getIndexStatus() == KbIndexStatus.FAILED);
            boolean allReady = files.stream().allMatch(f -> f.getIndexStatus() == KbIndexStatus.READY);
            if (anyIndexing) {
                this.status = KbStatus.INDEXING;
            } else if (anyFailed && !allReady) {
                this.status = KbStatus.PARTIAL_FAILED;
            } else {
                this.status = KbStatus.READY;
            }
        }
        validate();
        knowledgeBaseRepository.save(this);
    }

    public void markDisabled(String operatorId) {
        this.initialize(operatorId);
        this.status = KbStatus.DISABLED;
        knowledgeBaseRepository.save(this);
    }

    public void markEnabled(String operatorId) {
        this.initialize(operatorId);
        refreshAggregateStatus(operatorId);
    }

    @Override
    public void delete(String operatorId) {
        this.initialize(operatorId);
        this.setDeleted(1);
        knowledgeBaseRepository.save(this);
    }
}
