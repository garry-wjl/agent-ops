package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建工具入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>
 * 创建仅落草稿（status=DRAFT），发布走独立 publish 接口（工具管理技术方案 §0 共识 #11）。
 * workspaceNum 由 adapter 从当前空间上下文取得后传入；type / creationMode 建好不可改。
 * 各形态专有字段按 type + creationMode 选填（domain 发布时校验形态完整性）。
 */
@Data
public class ToolCreateParamDTO {

    /** 归属工作空间业务编号（必填，adapter 从上下文注入）。 */
    private String workspaceNum;

    /** 负责人 / 创建人用户 ID（必填，adapter 从上下文注入）。 */
    private String ownerUserId;

    /** 工具名称（必填，≤128 字符，工作空间内唯一）。 */
    private String name;

    /** 工具描述（必填，≤500 字符）。 */
    private String description;

    /** 工具类型（必填）：MCP / FUNCTION_CALL。 */
    private String type;

    /** 创建方式（必填）：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL。 */
    private String creationMode;

    /** 标签（可选，≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;

    // ---- MCP 远程连接专有（type=MCP, creationMode=REMOTE） ----

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文（≤64KB）。 */
    private String mcpConfig;

    // ---- MCP 代理（MCP 两形态共用） ----

    /** 是否启用平台 MCP 代理；默认 false。 */
    private Boolean proxyEnabled;

    /** 透传请求头（proxyEnabled=true 时可填，≤20 条）。 */
    private List<ProxyHeaderDTO> proxyHeaders;

    // ---- MCP API 打包专有（type=MCP, creationMode=API_PACKAGE） ----

    /** 打包方式：EXISTING_API / OPENAPI_PASTE。 */
    private String packageMode;

    /** 来源 FunctionCall 工具 num（packageMode=EXISTING_API 时必填）。 */
    private String sourceFcToolNum;

    // ---- OpenAPI 形态（FC-OPENAPI_SPEC / MCP-API_PACKAGE-OPENAPI_PASTE 共用） ----

    /** OpenAPI / Swagger JSON 原文（≤1MB）。 */
    private String openApiSpec;

    // ---- FunctionCall 手动录入专有（type=FUNCTION_CALL, creationMode=MANUAL） ----

    /** API Base URL（合法 URL，含 scheme）。 */
    private String baseUrl;

    /** API 端点列表（≥1 且 ≤50）。 */
    private List<ApiEndpointDTO> endpoints;
}
