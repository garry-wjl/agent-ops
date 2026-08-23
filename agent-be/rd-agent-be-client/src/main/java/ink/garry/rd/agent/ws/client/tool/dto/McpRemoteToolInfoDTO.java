package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 远程服务器上的单个工具摘要（试连 listTools 结果项）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRemoteToolInfoDTO {

    /** 工具名（function name）。 */
    private String name;

    /** 展示标题（可空）。 */
    private String title;

    /** 工具描述（可空）。 */
    private String description;

    /** 入参 JSON Schema（对应 MCP Tool.inputSchema；可空）。 */
    private Map<String, Object> inputSchema;

    /** 返回值 JSON Schema（对应 MCP Tool.outputSchema；可空）。 */
    private Map<String, Object> outputSchema;
}
