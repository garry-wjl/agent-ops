package ink.garry.rd.agent.ws.domain.session.valueobject;

/**
 * 消息角色枚举：标识会话中一条消息的发送方。
 */
public enum MessageRole {
    /** 用户输入消息。 */
    USER,
    /** Agent 助手回复消息。 */
    ASSISTANT,
    /** 工具调用消息（保留：Function/Tool 调用回执）。 */
    TOOL
}
