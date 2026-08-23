package ink.garry.rd.agent.ws.domain.knowledgebase.repository;

import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;

import java.util.List;

/**
 * 知识库索引任务仓储。
 */
public interface KbIndexTaskRepository {

    void save(KbIndexTask task);

    KbIndexTask findByNum(String taskNum);

    List<KbIndexTask> listPending(int limit);

    List<KbIndexTask> listByKbNumAndStatus(String kbNum, String status);
}
