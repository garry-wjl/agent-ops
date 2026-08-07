package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具 DTO（列表项 / 命令返回 / 详情主体，application 层边界）。
 * <p>
 * 承载工具全字段快照（含各形态专有字段 + 系统派生的 reuseCount / endpointMeta）；
 * adapter 由此转 {@code ToolVo} 返回客户端。列表场景部分大字段（mcpConfig / openApiSpec）
 * 可由 adapter 按需裁剪。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolDTO {

    /** 业务编号（MCP... / FC...）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 工具名称。 */
    private String name;

    /** 工具描述。 */
    private String description;

    /** 工具类型：MCP / FUNCTION_CALL。 */
    private String type;

    /** 创建方式：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL。 */
    private String creationMode;

    /** 标签。 */
    private List<String> tags;

    /** 状态：DRAFT / PUBLISHED / DEPRECATED。 */
    private String status;

    /** 复用数（被多少已发布 Agent 挂载，查询时实时统计）。 */
    private Integer reuseCount;

    // ---- MCP 远程连接专有 ----

    /** MCP 配置子类型：LOCAL / REMOTE。 */
    private String mcpConfigType;

    /** MCP 配置 JSON 原文。 */
    private String mcpConfig;

    // ---- MCP 代理 ----

    /** 是否启用平台 MCP 代理。 */
    private Boolean proxyEnabled;

    /** 透传请求头。 */
    private List<ProxyHeaderDTO> proxyHeaders;

    // ---- MCP API 打包专有 ----

    /** 打包方式：EXISTING_API / OPENAPI_PASTE。 */
    private String packageMode;

    /** 来源 FunctionCall 工具 num。 */
    private String sourceFcToolNum;

    // ---- OpenAPI 形态 ----

    /** OpenAPI / Swagger JSON 原文。 */
    private String openApiSpec;

    // ---- FunctionCall 手动录入专有 ----

    /** API Base URL。 */
    private String baseUrl;

    /** API 端点列表。 */
    private List<ApiEndpointDTO> endpoints;

    // ---- 系统派生 ----

    /** 发布时解析的端点元数据（OpenAPI 形态）。 */
    private EndpointMetaDTO endpointMeta;

    /** 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    /** 创建人工号。 */
    private String createNo;

    /** 更新人工号。 */
    private String updateNo;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
