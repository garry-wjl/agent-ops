package ink.garry.rd.agent.ws.application.agentrunner.run;

import java.util.List;
import java.util.Optional;

/**
 * Agent run 事件缓冲（Redis Stream）与元数据存取。
 */
public interface AgentRunEventStore {

    /**
     * 创建 RUNNING run，并登记 session → run 索引。
     *
     * @param runId      run 编号
     * @param sessionNum 会话编号
     * @param agentNum   Agent 编号
     */
    void createRun(String runId, String sessionNum, String agentNum);

    /**
     * 追加一帧事件（seq 由调用方分配，须单调递增）。
     *
     * @param runId run 编号
     * @param seq   序号
     * @param event SSE event 名，可空
     * @param data  JSON data
     */
    void append(String runId, long seq, String event, String data);

    /**
     * 标记 run 终态并清理 session 进行中索引。
     *
     * @param runId  run 编号
     * @param status 终态
     */
    void complete(String runId, AgentRunStatus status);

    /**
     * @param sessionNum 会话编号
     * @return 进行中的 runId，无则 empty
     */
    Optional<String> findRunningRunId(String sessionNum);

    /**
     * @param runId run 编号
     * @return 元数据，不存在则 empty
     */
    Optional<AgentRunMeta> getMeta(String runId);

    /**
     * 读取 seq &gt; afterSeq 的已缓冲事件（按 seq 升序）。
     *
     * @param runId    run 编号
     * @param afterSeq 已确认序号
     * @return 事件列表
     */
    List<AgentRunStreamEvent> listAfter(String runId, long afterSeq);

    /**
     * run 元数据。
     */
    record AgentRunMeta(
            String runId,
            String sessionNum,
            String agentNum,
            AgentRunStatus status,
            long lastSeq
    ) {
    }
}
