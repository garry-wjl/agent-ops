package ink.garry.rd.agent.ws.domain.workspace.repository;

import ink.garry.rd.agent.ws.domain.workspace.valueobject.WorkspaceKbConfig;

/**
 * 工作空间知识库配置仓储。
 */
public interface WorkspaceKbConfigRepository {

    void save(WorkspaceKbConfig config, String operatorId);

    WorkspaceKbConfig findByWorkspaceNum(String workspaceNum);
}
