package ink.garry.rd.agent.ws.domain.knowledgebase.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 知识库领域工厂。
 */
@Component
public class KnowledgeBaseFactory {

    @Resource
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Resource
    private KnowledgeBaseFileRepository knowledgeBaseFileRepository;
    @Resource
    private KbNumGateway kbNumGateway;

    public KnowledgeBase create(String workspaceNum,
                                String name,
                                String description,
                                KbType kbType,
                                KbIndexConfig indexConfig,
                                KbRetrievalDefaults retrievalDefaults) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "name 不能为空");
        Assert.notNull(kbType, "kbType 不能为空");
        Assert.notNull(indexConfig, "indexConfig 不能为空");
        return new KnowledgeBase(workspaceNum, name, description, kbType, indexConfig,
                retrievalDefaults, knowledgeBaseRepository, knowledgeBaseFileRepository, kbNumGateway);
    }

    public KnowledgeBase createByNum(String kbNum) {
        Assert.notBlank(kbNum, "kbNum 不能为空");
        KnowledgeBase kb = knowledgeBaseRepository.findByNum(kbNum);
        if (kb == null) {
            return null;
        }
        wireKb(kb);
        return kb;
    }

    public KnowledgeBase requireByNum(String kbNum) {
        KnowledgeBase kb = createByNum(kbNum);
        if (kb == null) {
            throw new BusinessException(1201, "知识库不存在");
        }
        return kb;
    }

    public KnowledgeBaseFile createFile(String ossFileId,
                                        String fileName,
                                        String mimeType,
                                        Long fileSize) {
        KnowledgeBaseFile file = new KnowledgeBaseFile();
        file.setOssFileId(ossFileId);
        file.setFileName(fileName);
        file.setMimeType(mimeType);
        file.setFileSize(fileSize);
        file.setKnowledgeBaseFileRepository(knowledgeBaseFileRepository);
        return file;
    }

    public KnowledgeBaseFile createFileByNum(String fileNum) {
        KnowledgeBaseFile file = knowledgeBaseFileRepository.findByNum(fileNum);
        if (file != null) {
            file.setKnowledgeBaseFileRepository(knowledgeBaseFileRepository);
        }
        return file;
    }

    private void wireKb(KnowledgeBase kb) {
        kb.setKnowledgeBaseRepository(knowledgeBaseRepository);
        kb.setKnowledgeBaseFileRepository(knowledgeBaseFileRepository);
        kb.setKbNumGateway(kbNumGateway);
    }
}
