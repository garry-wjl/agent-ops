package ink.garry.rd.agent.ws.domain.agent.repository;

import ink.garry.rd.agent.ws.domain.agent.Agent;

/**
 * Agent 聚合根仓储接口（CLAUDE.md §3.5 硬约束：仅 3 方法）。
 * <p>
 * 其他读能力：
 * <ul>
 *   <li>分页 / 列表 / 按属主等 → {@code gateway/AgentGateway}</li>
 *   <li>按 Nacos 服务幂等键查 num → {@code AgentQueryService.findNumByNacosServiceKey}
 *       （订阅路径直查 Mapper，命中后用 {@link #findByNum} 重建领域对象）</li>
 * </ul>
 */
public interface AgentRepository {

    /**
     * 持久化 Agent（新建 INSERT，已有 UPDATE）。
     *
     * @param aggregate 待保存的聚合根
     */
    void save(Agent aggregate);

    /**
     * 按业务编号加载 Agent。
     *
     * @param num Agent 业务编号
     * @return 聚合根；不存在返回 null
     */
    Agent findByNum(String num);

    /**
     * 按业务编号删除 Agent（软删或物理删由实现决定）。
     *
     * @param num Agent 业务编号
     */
    void deleteByNum(String num);
}
