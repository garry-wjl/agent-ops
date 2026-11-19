package ink.garry.rd.agent.ws.domain.team.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 一次编排运行。snapshotJson 冻结启动时的定义，runtimeJson 是当前节点状态。
 */
public interface AgentTeamRunRepository {

    /**
     * 运行行。ossSubPath 是这块运行要挂到每个 Agent 容器的 OSS 前缀。
     */
    record RunRecord(String num, String teamNum, String workspaceNum, String teamName, String mode,
                     String inputText, String status, String ossSubPath, String snapshotJson,
                     String runtimeJson, String updateNo, LocalDateTime createTime) {
    }

    void insert(RunRecord record, String operatorId);

    void updateRuntime(String num, String status, String runtimeJson, String operatorId);

    Optional<RunRecord> find(String workspaceNum, String num);

    List<RunRecord> list(String workspaceNum);
}
