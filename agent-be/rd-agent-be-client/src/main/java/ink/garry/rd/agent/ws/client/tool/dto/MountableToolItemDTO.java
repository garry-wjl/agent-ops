package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 可挂载的「具体工具」项（展平后的 FC 端点或 MCP 远端工具）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MountableToolItemDTO {

    /**
     * 稳定绑定键（与 {@code ToolRef.bindingKey()} 对齐），前端多选 value。
     */
    private String bindingKey;

    /** 所属工具资产编号。 */
    private String toolNum;

    /** 所属工具资产名称（组名）。 */
    private String toolName;

    /** 资产类型：MCP / FUNCTION_CALL。 */
    private String toolType;

    /** 具体项类型：FC_ENDPOINT / MCP_TOOL。 */
    private String itemKind;

    /** 展示名（端点摘要或 MCP tool name）。 */
    private String name;

    /** 可选标题（MCP title）。 */
    private String title;

    /** 描述。 */
    private String description;

    /** FC：HTTP 方法。 */
    private String method;

    /** FC：路径。 */
    private String path;

    /** MCP：远端工具名。 */
    private String mcpToolName;
}
