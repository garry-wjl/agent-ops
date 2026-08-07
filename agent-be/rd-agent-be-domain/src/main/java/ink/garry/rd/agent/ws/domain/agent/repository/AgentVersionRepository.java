package ink.garry.rd.agent.ws.domain.agent.repository;

import ink.garry.rd.agent.ws.domain.agent.AgentVersion;

/**
 * Agent 版本仓储接口（仅 3 方法）。
 * <p>
 * 其他读能力（findCurrent / listByAgentNum）见 gateway/AgentVersionGateway。
 */
public interface AgentVersionRepository {

    /**
     * 持久化版本；版本不可变，常规仅 INSERT。
     * 切换 currentFlag 等修改通过 AgentVersionGateway.switchCurrent。
     *
     * @param aggregate 待保存的版本实体
     */
    void save(AgentVersion aggregate);

    /**
     * 按业务编号加载版本。
     *
     * @param num 版本业务编号
     * @return 版本实体；不存在返回 null
     */
    AgentVersion findByNum(String num);

    /**
     * 按业务编号删除版本（仅在 Agent 删除级联归档时使用）。
     *
     * @param num 版本业务编号
     */
    void deleteByNum(String num);
}
