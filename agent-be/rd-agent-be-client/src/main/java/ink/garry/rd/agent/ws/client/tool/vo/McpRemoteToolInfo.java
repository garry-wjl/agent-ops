package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

import java.util.Map;

/**
 * MCP 远程服务器工具摘要（试连 listTools 出参）。
 */
@Data
public class McpRemoteToolInfo {

    /** 工具名。 */
    private String name;

    /** 展示标题。 */
    private String title;

    /** 工具描述。 */
    private String description;

    /** 入参 JSON Schema。 */
    private Map<String, Object> inputSchema;

    /** 返回值 JSON Schema。 */
    private Map<String, Object> outputSchema;
}
