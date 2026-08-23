package ink.garry.rd.agent.ws.domain.knowledgebase.repository;

import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;

/**
 * 知识库聚合仓储。
 */
public interface KnowledgeBaseRepository {

    void save(KnowledgeBase aggregate);

    KnowledgeBase findByNum(String kbNum);

    boolean existsByWorkspaceAndName(String workspaceNum, String name, String excludeNum);
}
