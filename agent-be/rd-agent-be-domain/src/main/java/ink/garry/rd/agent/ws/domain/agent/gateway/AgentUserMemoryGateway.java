package ink.garry.rd.agent.ws.domain.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.AgentUserMemory;

/**
 * 用户长期记忆读写。
 */
public interface AgentUserMemoryGateway {

    /**
     * 按工作空间 + Agent + 用户加载。不存在返回 null。
     */
    AgentUserMemory find(String workspaceNum, String agentNum, String userId);

    /**
     * 插入或按唯一键更新。
     */
    void upsert(AgentUserMemory memory);
}
