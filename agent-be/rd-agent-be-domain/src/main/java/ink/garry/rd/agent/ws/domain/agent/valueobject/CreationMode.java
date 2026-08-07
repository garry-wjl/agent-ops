package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * Agent 创建方式（2 种）。详见技术方案 v2.0 §6.2。
 * <p>
 * 决定 application 层派发到哪个 AgentRunner 实现：
 * <ul>
 *   <li>{@link #CONFIG} → {@code application.agent.strategy.ConfigAgentRunner}</li>
 *   <li>{@link #A2A} → {@code application.agent.strategy.A2aAgentRunner}</li>
 * </ul>
 * 历史枚举 ACP / MCP / API 已在 v2.0 移除（不进路线图）。
 */
public enum CreationMode {
    /** 平台原生配置；用户在页面新建；Runner 直接调用模型；可挂 Skill / 子 Agent */
    CONFIG,
    /** 通过订阅 Nacos {@code a2a-agents} group 自动发现的 A2A Agent；平台只读，所有信息（含状态）以 Nacos 为权威源 */
    A2A
}
