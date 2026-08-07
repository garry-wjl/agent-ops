package ink.garry.rd.agent.ws.facade.common;

/**
 * 输入类型枚举。
 * 用于标识 Skill / Agent 入参的承载格式，便于上下游做相应的解析与校验。
 */
public enum InputType {
    /** 纯文本输入 */
    TEXT,
    /** JSON 结构化输入 */
    JSON
}
