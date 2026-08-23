package ink.garry.rd.agent.ws.domain.knowledgebase.repository;

import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;

import java.util.List;

/**
 * 知识库文件仓储。
 */
public interface KnowledgeBaseFileRepository {

    void save(KnowledgeBaseFile file);

    KnowledgeBaseFile findByNum(String fileNum);

    List<KnowledgeBaseFile> listByKbNum(String kbNum);

    int countByKbNum(String kbNum);

    int countIndexedByKbNum(String kbNum);
}
