package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * MCP 配置子类型枚举（仅 {@link ToolType#MCP} + {@link CreationMode#REMOTE} 使用）。
 * <p>
 * 决定 {@code mcpConfig} JSON 串的字段结构与前端 / 后端校验 schema（详见 PRD §7.3）。
 */
public enum McpConfigType {

    /** 本地 MCP server（stdio 传输）：mcpConfig 走 command / args / env 结构。 */
    LOCAL,

    /** 远程 MCP server（sse / streamable-http 传输）：mcpConfig 走 url / transport / headers 结构。 */
    REMOTE
}
