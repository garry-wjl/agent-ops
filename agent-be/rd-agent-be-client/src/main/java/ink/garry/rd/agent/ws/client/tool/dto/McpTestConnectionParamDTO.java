/**
 * MCP 测试连接入参 DTO（application 层边界）。
 * <p>
 * 用于用户在编辑/新建 MCP 工具时点击「测试连接」按钮触发后端验证；
 * 覆盖远程 MCP 标准字段：mcpConfig（JSON 原文）、mcpConfigType、代理相关。
 */
package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpTestConnectionParamDTO {

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文（含 url / transport / headers 等）。 */
    private String mcpConfig;

    /** 是否启用平台 MCP 代理。 */
    private Boolean proxyEnabled;

    /** 透传请求头。 */
    private List<ProxyHeaderDTO> proxyHeaders;
}