package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * API 参数数据类型枚举（FunctionCall 手动录入的 {@link ApiParam#getType()} 使用）。
 * <p>
 * 详见 PRD §7.6：defaultValue 以字符串形式存储，运行时按本类型反序列化。
 */
public enum ApiParamType {

    /** 字符串。 */
    STRING,

    /** 数值（浮点）。 */
    NUMBER,

    /** 布尔。 */
    BOOLEAN,

    /** 整数。 */
    INTEGER
}
