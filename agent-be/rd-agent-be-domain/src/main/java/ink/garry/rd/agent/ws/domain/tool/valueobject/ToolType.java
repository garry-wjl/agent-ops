package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * 工具类型枚举。
 * <p>
 * 工具类型在创建后<b>不可修改</b>（编辑态 disabled，要换形态须新建）；
 * 决定可选的创建方式（详见 {@link CreationMode}）与业务编号前缀。
 */
public enum ToolType {

    /** MCP 工具：Model Context Protocol，承载远程连接 / API 打包两种创建方式；业务编号前缀 MCP。 */
    MCP,

    /** FunctionCall 工具：HTTP API 调用，承载 OpenAPI Spec 导入 / 手动录入两种创建方式；业务编号前缀 FC。 */
    FUNCTION_CALL
}
