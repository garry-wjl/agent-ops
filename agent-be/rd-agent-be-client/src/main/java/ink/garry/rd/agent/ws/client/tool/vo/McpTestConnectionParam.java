/**
 * MCP 测试连接入参 Vo（adapter 层）。
 * <p>
 * 用户在编辑/新建远程 MCP 工具时，在配置输入框中点击「测试连接」按钮后提交的参数。
 */
package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

import java.util.List;

@Data
public class McpTestConnectionParam {

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文（含 url / transport / headers 等）。 */
    private String mcpConfig;

    /** 是否启用平台 MCP 代理。 */
    private Boolean proxyEnabled;

    /** 透传请求头。 */
    private List<ProxyHeaderVo> proxyHeaders;
}