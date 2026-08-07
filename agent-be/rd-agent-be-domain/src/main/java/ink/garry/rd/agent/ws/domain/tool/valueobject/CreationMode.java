package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * 工具创建方式枚举（与 {@link ToolType} 组合使用，创建后不可修改）。
 * <p>
 * 合法组合（详见工具管理技术方案 §7.1.2）：
 * <ul>
 *   <li>{@link ToolType#MCP} + {@link #REMOTE}：远程连接，用户自备 MCP server endpoint。</li>
 *   <li>{@link ToolType#MCP} + {@link #API_PACKAGE}：API 打包，把已有 API 或 OpenAPI 文档包成 MCP 工具。</li>
 *   <li>{@link ToolType#FUNCTION_CALL} + {@link #OPENAPI_SPEC}：粘贴 OpenAPI 规范文档解析。</li>
 *   <li>{@link ToolType#FUNCTION_CALL} + {@link #MANUAL}：Base URL + 多端点逐项配置。</li>
 * </ul>
 */
public enum CreationMode {

    /** MCP 远程连接：用户自备 MCP server endpoint，平台只存配置并转发。 */
    REMOTE,

    /** MCP API 打包：把已有 FC 工具或一段 OpenAPI 文档"包"成 MCP 工具暴露给 Agent。 */
    API_PACKAGE,

    /** FunctionCall OpenAPI Spec 导入：粘贴 JSON 规范文档由后端解析端点元数据。 */
    OPENAPI_SPEC,

    /** FunctionCall 手动录入：Base URL + 多端点逐项配置。 */
    MANUAL
}
