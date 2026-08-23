package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Agent 配置中绑定的工具引用（可绑到资产内的具体可调用项）。
 * <p>
 * 仅填 {@code toolNum} 表示整组挂载（兼容旧数据）；新写入须带 {@code itemKind}。
 */
@Data
public class ToolRefParam {

    /** FunctionCall 端点。 */
    public static final String ITEM_FC_ENDPOINT = "FC_ENDPOINT";
    /** MCP 远端工具。 */
    public static final String ITEM_MCP_TOOL = "MCP_TOOL";

    /** 工具资产业务编号。 */
    private String toolNum;

    /** 发布版本号；当前 Tool 无版本表时可为空。 */
    private String versionNum;

    /** 具体项类型：FC_ENDPOINT / MCP_TOOL；空=整组。 */
    private String itemKind;

    /** FC：HTTP 方法。 */
    private String method;

    /** FC：路径。 */
    private String path;

    /** MCP：远端工具名。 */
    private String mcpToolName;
}
