package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * 输入模式（调试台 / invoke 接口共享）。
 * <p>
 * 决定 InvokeContext.input 的反序列化与展示形态。
 */
public enum InputType {
    /** 文本输入（input 为 String） */
    TEXT,
    /** 结构化 JSON 输入（input 为 Map / 对象） */
    JSON
}
