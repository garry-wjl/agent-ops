package ink.garry.rd.agent.ws.domain.knowledgebase.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文件子实体。
 */
@Getter
@Setter
public class KnowledgeBaseFile extends DomainEntity {

    private String num;
    private String kbNum;
    private String ossFileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private KbIndexStatus indexStatus;
    private KbIndexConfig indexConfigSnapshot;
    private Integer chunkCount;
    private List<String> vectorDocIds;
    private String errorMessage;
    private LocalDateTime indexedAt;

    private transient KnowledgeBaseFileRepository knowledgeBaseFileRepository;

    public void markIndexing(String operatorId) {
        this.initialize(operatorId);
        this.indexStatus = KbIndexStatus.PARSING;
        this.errorMessage = null;
        knowledgeBaseFileRepository.save(this);
    }

    public void markReady(KbIndexConfig snapshot, int chunkCount, List<String> vectorDocIds, String operatorId) {
        this.initialize(operatorId);
        this.indexStatus = KbIndexStatus.READY;
        this.indexConfigSnapshot = snapshot;
        this.chunkCount = chunkCount;
        this.vectorDocIds = vectorDocIds;
        this.errorMessage = null;
        this.indexedAt = LocalDateTime.now();
        knowledgeBaseFileRepository.save(this);
    }

    public void markFailed(String error, String operatorId) {
        this.initialize(operatorId);
        this.indexStatus = KbIndexStatus.FAILED;
        this.errorMessage = StrUtil.sub(error, 0, 1000);
        knowledgeBaseFileRepository.save(this);
    }

    public void markDeleted(String operatorId) {
        this.initialize(operatorId);
        this.setDeleted(1);
        knowledgeBaseFileRepository.save(this);
    }

    @Override
    public void domainValidate() {
        // file entity validated on register/index paths
    }

    @Override
    public void save(String operatorId) {
        this.initialize(operatorId);
        knowledgeBaseFileRepository.save(this);
    }

    @Override
    public void delete(String operatorId) {
        markDeleted(operatorId);
    }
}
