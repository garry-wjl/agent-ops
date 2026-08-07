package ink.garry.rd.agent.ws.domain.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentType;

/**
 * Agent 工厂接口（实现位于 infra）。
 * <p>
 * 负责装配聚合根所需的 Repository / Gateway / Publisher，使聚合可独立 save/delete/业务方法。
 * 拆为 CONFIG 与 A2A 两个独立工厂方法，避免参数越界（A2A 不接受 prompt/skill 等配置；
 * CONFIG 不接受 a2aSource）。
 */
public interface AgentFactory {

    /**
     * 创建配置模式 Agent 聚合根（仅必填字段，状态由 save 初始化为 DRAFT_ONLY）。
     * <p>
     * creationMode 由本工厂方法固定为 {@code CONFIG}，调用方无需传。
     *
     * @param name        Agent 显示名
     * @param description 描述
     * @param agentType   行为类型（NORMAL / SUPERVISOR / ROUTER）
     * @param ownerUserId 负责人 userId
     * @param tags        业务标签，可空
     * @return 已装配依赖、未持久化的 Agent 实例
     */
    Agent createConfigAgent(String name, String description, AgentType agentType, String ownerUserId,
                            java.util.List<String> tags);

    /**
     * 创建 A2A 模式 Agent 聚合根（首次从 Nacos 发现时调用）。
     * <p>
     * creationMode 固定为 {@code A2A}；agentType 固定为 {@code NORMAL}（A2A 内部行为模式
     * 平台无法感知，统一记普通）；ownerUserId 固定为 {@code system}；sandbox 永远 false。
     * 状态由调用方传入：实例健康 = PUBLISHED，下线 = OFFLINE。
     *
     * @param source      Nacos 来源信息（含 service key、endpoint、Agent Card 等）
     * @param name        Agent Card 中的 name
     * @param description Agent Card 中的 description，可空
     * @param status      初始状态（按 Nacos instance.healthy 映射）
     * @return 已装配依赖、未持久化的 Agent 实例
     */
    Agent createA2aAgent(A2aSourceInfo source, String name, String description, AgentStatus status);

    /**
     * 通过 num 重建（委托 AgentRepository.findByNum）。
     *
     * @param num Agent 业务编号
     * @return 已装配依赖的 Agent；不存在返回 null
     */
    Agent createByNum(String num);
}
