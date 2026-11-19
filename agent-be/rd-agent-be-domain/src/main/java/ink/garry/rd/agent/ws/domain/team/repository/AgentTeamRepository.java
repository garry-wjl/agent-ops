package ink.garry.rd.agent.ws.domain.team.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Agent Team 定义仓储。
 */
public interface AgentTeamRepository {

    /**
     * 团队定义行。definitionJson 是画布或 Leader/Worker 的 JSON。
     */
    record TeamRecord(String num, String workspaceNum, String name, String description, String mode,
                      String status, String definitionJson, String updateNo, LocalDateTime updateTime) {
    }

    void insert(TeamRecord record, String operatorId);

    void update(TeamRecord record, String operatorId);

    Optional<TeamRecord> find(String workspaceNum, String num);

    List<TeamRecord> list(String workspaceNum);

    void softDelete(String workspaceNum, String num, String operatorId);
}
