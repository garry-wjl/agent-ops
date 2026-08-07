package ink.garry.rd.agent.ws.client.tool.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建工具入参 Vo（adapter 层，来自 HTTP 请求体）。
 * <p>
 * 创建仅落草稿（status=DRAFT），发布走独立 publish 接口。workspaceNum / ownerUserId 不在请求体，
 * 由 adapter 从当前空间上下文 / 登录用户注入。type / creationMode 建好不可改。
 * 各形态专有字段按 type + creationMode 选填（domain 发布时校验形态完整性）。
 */
@Data
public class ToolCreateParam {

    /** 工具名称（必填，≤128 字符，工作空间内唯一）。 */
    @NotBlank(message = "工具名称不能为空")
    private String name;

    /** 工具描述（可选，≤500 字符；草稿可为空，发布时必填）。 */
    private String description;

    /** 工具类型（必填）：MCP / FUNCTION_CALL。 */
    @NotBlank(message = "工具类型不能为空")
    private String type;

    /** 创建方式（必填）：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL。 */
    @NotBlank(message = "工具创建方式不能为空")
    private String creationMode;

    /** 标签（可选，≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;

    // ---- MCP 远程连接专有 ----

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文（≤64KB）。 */
    private String mcpConfig;

    // ---- MCP 代理 ----

    /** 是否启用平台 MCP 代理；默认 false。 */
    private Boolean proxyEnabled;

    /** 透传请求头（proxyEnabled=true 时可填，≤20 条）。 */
    private List<ProxyHeaderVo> proxyHeaders;

    // ---- MCP API 打包专有 ----

    /** 打包方式：EXISTING_API / OPENAPI_PASTE。 */
    private String packageMode;

    /** 来源 FunctionCall 工具 num（packageMode=EXISTING_API 时必填）。 */
    private String sourceFcToolNum;

    // ---- OpenAPI 形态 ----

    /** OpenAPI / Swagger JSON 原文（≤1MB）。 */
    private String openApiSpec;

    // ---- FunctionCall 手动录入专有 ----

    /** API Base URL（合法 URL，含 scheme）。 */
    private String baseUrl;

    /** API 端点列表（≥1 且 ≤50）。 */
    private List<ApiEndpointVo> endpoints;
}
