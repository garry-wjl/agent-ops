package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.Data;

import java.util.List;

/**
 * 编辑工具入参 DTO（application 层边界）。
 * <p>
 * 编辑任何字段后工具状态切回 DRAFT（工具管理技术方案 §4.2 / PRD §7.7.2）；
 * type / creationMode 只读（不在此 DTO 提供，编辑态 disabled）。各形态专有字段按工具实际形态选填。
 */
@Data
public class ToolUpdateParamDTO {

    /** 工具业务编号（必填，定位被编辑工具）。 */
    private String num;

    /** 工具名称（≤128 字符）。 */
    private String name;

    /** 工具描述（≤500 字符）。 */
    private String description;

    /** 标签（≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;

    // ---- MCP 远程连接专有 ----

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文（≤64KB）。 */
    private String mcpConfig;

    // ---- MCP 代理 ----

    /** 是否启用平台 MCP 代理。 */
    private Boolean proxyEnabled;

    /** 透传请求头（≤20 条）。 */
    private List<ProxyHeaderDTO> proxyHeaders;

    // ---- MCP API 打包专有 ----

    /** 打包方式：EXISTING_API / OPENAPI_PASTE。 */
    private String packageMode;

    /** 来源 FunctionCall 工具 num。 */
    private String sourceFcToolNum;

    // ---- OpenAPI 形态 ----

    /** OpenAPI / Swagger JSON 原文（≤1MB）。 */
    private String openApiSpec;

    // ---- FunctionCall 手动录入专有 ----

    /** API Base URL。 */
    private String baseUrl;

    /** API 端点列表（≥1 且 ≤50）。 */
    private List<ApiEndpointDTO> endpoints;
}
