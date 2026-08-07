package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * 短期记忆策略枚举（v2.5 §10.2）。
 * <p>
 * 控制运行时短期记忆窗口的截取方式；与 {@code MemoryConfig.shortTermN} 配合使用。
 */
public enum ShortTermMemoryStrategy {

    /** 不启用短期记忆，每次调用独立无上下文 */
    NONE,

    /** 取最近 N 轮对话作为上下文（N 由 {@code shortTermN} 指定） */
    LAST_N_TURNS,

    /** 取整个 Session 历史，由 runner 自行做 token 控制 */
    FULL_SESSION
}
