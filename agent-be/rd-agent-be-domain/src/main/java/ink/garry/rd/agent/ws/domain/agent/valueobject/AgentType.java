package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * Agent 行为类型（决定执行模型与可挂载结构）。
 * <p>
 * 接入式 Agent（CONFIG 以外）只能是 NORMAL，约束在 Agent.domainValidate。
 */
public enum AgentType {
    /** 普通：单一职责，挂载 Skill */
    NORMAL,
    /** 监督者：编排子 Agent；子 Agent 必须为 NORMAL */
    SUPERVISOR,
    /** 路由：按意图分发到候选 NORMAL Agent */
    ROUTER
}
