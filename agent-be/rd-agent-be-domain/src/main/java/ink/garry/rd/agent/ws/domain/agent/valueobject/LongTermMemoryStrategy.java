package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * 长期记忆策略枚举（v2.5 §10.2）。
 * <p>
 * 控制是否在调用时检索 / 写入用户画像与历史偏好。
 */
public enum LongTermMemoryStrategy {

    /** 不启用长期记忆 */
    NONE,

    /** 启用长期记忆：每轮按用户 / Agent 维度检索并合并到 systemPrompt */
    ENABLED
}
