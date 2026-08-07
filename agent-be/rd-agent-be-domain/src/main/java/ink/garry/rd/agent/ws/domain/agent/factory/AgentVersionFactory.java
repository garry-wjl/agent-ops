package ink.garry.rd.agent.ws.domain.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.Version;

/**
 * AgentVersion 工厂接口（实现位于 infra）。
 * <p>
 * 负责装配版本实体所需的 Repository / Gateway / Publisher，发布动作由 application 编排。
 */
public interface AgentVersionFactory {

    /**
     * 创建新版本（不可变 snapshot）。
     * <p>
     * v3.1（Agent 优化）：移除 {@code changeLevel} 入参，版本号统一按 {@link Version#next()} patch+1 推进。
     *
     * @param agentNum    关联的 Agent 业务编号
     * @param version     新版本号值对象（基于上一版本 {@link Version#next()} 推进）
     * @param snapshot    本次发布的完整配置快照
     * @param remark      发布备注，长度需 ≥ 10 字符
     * @param publishedBy 发布人 userId
     * @return 已装配依赖、未持久化的 AgentVersion 实例
     */
    AgentVersion create(String agentNum, Version version,
                        ConfigSnapshot snapshot, String remark, String publishedBy);

    /**
     * 通过 num 从仓储加载并重建版本（用于回滚等需要修改 currentFlag 场景）。
     *
     * @param num 版本业务编号
     * @return 已装配依赖的 AgentVersion；不存在返回 null
     */
    AgentVersion createByNum(String num);
}
