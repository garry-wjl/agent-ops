package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * Agent 行为类型（决定执行模型与可挂载结构）。
 * <p>
 * 接入式 Agent（CONFIG 以外）只能是 NORMAL，约束在 Agent.domainValidate。
 */
public enum AgentType {
    /** 普通：单一职责，挂载 Skill */
    NORMAL,
    /**
     * 历史类型，不再支持新建。保留枚举值，避免已入库的行在反序列化时失败。
     */
    SUPERVISOR,
    /**
     * 历史类型，不再支持新建。保留枚举值，避免已入库的行在反序列化时失败。
     */
    ROUTER
}
