package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * Agent 可挂载的具体工具项（FE 多选）。
 */
@Data
public class MountableToolItemVo {

    /** 稳定绑定键。 */
    private String bindingKey;
    /** 所属工具资产编号。 */
    private String toolNum;
    /** 所属工具资产名称。 */
    private String toolName;
    /** MCP / FUNCTION_CALL。 */
    private String toolType;
    /** FC_ENDPOINT / MCP_TOOL。 */
    private String itemKind;
    /** 展示名。 */
    private String name;
    /** MCP title。 */
    private String title;
    /** 描述。 */
    private String description;
    /** FC method。 */
    private String method;
    /** FC path。 */
    private String path;
    /** MCP tool name。 */
    private String mcpToolName;
}
