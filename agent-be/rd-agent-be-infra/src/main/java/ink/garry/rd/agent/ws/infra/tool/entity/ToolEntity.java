package ink.garry.rd.agent.ws.infra.tool.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiEndpoint;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointMeta;
import ink.garry.rd.agent.ws.domain.tool.valueobject.McpConfigType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ProxyHeader;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具资产持久化实体（对应表 {@code tool}）。
 * <p>
 * 与 domain {@link Tool} 一一对应：枚举 {@code type} / {@code creation_mode} / {@code status} /
 * {@code mcp_config_type} / {@code package_mode} 以字符串列落库；{@code tags} / {@code proxy_headers} /
 * {@code endpoints} / {@code endpoint_meta} 以 JSON 列落库（fastjson2 序列化，与 AgentEntity 同模式）；
 * {@code mcp_config} / {@code open_api_spec} 为 text 原文直存。transient 依赖
 * （Repository / Gateway / Publisher）由 {@code ToolFactory} 装配，不在此映射。
 */
@Data
@TableName("tool")
public class ToolEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 MCP / FC，由 {@code ToolGateway.generateToolNum} 经 BizNumGenerator 生成） */
    private String num;

    /** 归属工作空间业务编号 */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 工具名称；同工作空间内唯一（不区分类型） */
    private String name;

    /** 工具描述；≤500 字 */
    private String description;

    /** 工具类型：MCP / FUNCTION_CALL（对应 {@link ToolType}） */
    private String type;

    /** 创建方式：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL（对应 {@link CreationMode}） */
    @TableField("creation_mode")
    private String creationMode;

    /** 标签 JSON 数组，由 fastjson2 序列化 List&lt;String&gt; */
    private String tags;

    /** 状态：DRAFT / PUBLISHED / DEPRECATED（对应 {@link ToolStatus}） */
    private String status;

    /** MCP 配置子类型：LOCAL / REMOTE（仅 MCP-REMOTE，对应 {@link McpConfigType}） */
    @TableField("mcp_config_type")
    private String mcpConfigType;

    /** MCP 配置 JSON 串原文（≤64KB，仅 MCP-REMOTE），text 列 */
    @TableField("mcp_config")
    private String mcpConfig;

    /** 是否启用 MCP 代理 0/1 */
    @TableField("proxy_enabled")
    private Integer proxyEnabled;

    /** 透传请求头 JSON 数组，由 fastjson2 序列化 List&lt;ProxyHeader&gt; */
    @TableField("proxy_headers")
    private String proxyHeaders;

    /** MCP API 打包方式：EXISTING_API / OPENAPI_PASTE（仅 MCP-API_PACKAGE，对应 {@link PackageMode}） */
    @TableField("package_mode")
    private String packageMode;

    /** 来源 FunctionCall 工具 num（仅 EXISTING_API，动态跟随） */
    @TableField("source_fc_tool_num")
    private String sourceFcToolNum;

    /** OpenAPI / Swagger JSON 原文（≤1MB，OPENAPI_SPEC / OPENAPI_PASTE），mediumtext 列 */
    @TableField("open_api_spec")
    private String openApiSpec;

    /** FunctionCall 手动录入 Base URL（仅 FC-MANUAL） */
    @TableField("base_url")
    private String baseUrl;

    /** API 端点 JSON 数组，由 fastjson2 序列化 List&lt;ApiEndpoint&gt;（仅 FC-MANUAL） */
    private String endpoints;

    /** 发布时解析的端点元数据 JSON，由 fastjson2 序列化 EndpointMeta（OpenAPI 形态） */
    @TableField("endpoint_meta")
    private String endpointMeta;

    /** 负责人 / 创建人用户 ID */
    @TableField("owner_user_id")
    private String ownerUserId;

    /** 创建人工号 */
    @TableField("create_no")
    private String createNo;

    /** 更新人工号（兼任删除人语义） */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（兼任删除时间语义） */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain。
     * <p>枚举字符串列反序列化为枚举；JSON 列经 fastjson2 反序列化为值对象；
     * proxy_enabled 整型 0/1 转 Boolean；transient 依赖由调用方（ToolFactory）装配。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域聚合根；e 为 null 返回 null
     */
    public static Tool toDomain(ToolEntity e) {
        if (e == null) {
            return null;
        }
        Tool t = new Tool();
        t.setId(e.getId());
        t.setNum(e.getNum());
        t.setWorkspaceNum(e.getWorkspaceNum());
        t.setName(e.getName());
        t.setDescription(e.getDescription());
        t.setType(e.getType() == null ? null : ToolType.valueOf(e.getType()));
        t.setCreationMode(e.getCreationMode() == null ? null : CreationMode.valueOf(e.getCreationMode()));
        t.setTags(e.getTags() == null ? null : JSON.parseArray(e.getTags(), String.class));
        t.setStatus(e.getStatus() == null ? null : ToolStatus.valueOf(e.getStatus()));
        t.setMcpConfigType(e.getMcpConfigType() == null ? null : McpConfigType.valueOf(e.getMcpConfigType()));
        t.setMcpConfig(e.getMcpConfig());
        t.setProxyEnabled(e.getProxyEnabled() == null ? null : e.getProxyEnabled() == 1);
        t.setProxyHeaders(e.getProxyHeaders() == null ? null
                : JSON.parseArray(e.getProxyHeaders(), ProxyHeader.class));
        t.setPackageMode(e.getPackageMode() == null ? null : PackageMode.valueOf(e.getPackageMode()));
        t.setSourceFcToolNum(e.getSourceFcToolNum());
        t.setOpenApiSpec(e.getOpenApiSpec());
        t.setBaseUrl(e.getBaseUrl());
        t.setEndpoints(e.getEndpoints() == null ? null
                : JSON.parseArray(e.getEndpoints(), ApiEndpoint.class));
        t.setEndpointMeta(e.getEndpointMeta() == null ? null
                : JSON.parseObject(e.getEndpointMeta(), new TypeReference<EndpointMeta>() {
                }));
        t.setOwnerUserId(e.getOwnerUserId());
        t.setCreateNo(e.getCreateNo());
        t.setUpdateNo(e.getUpdateNo());
        t.setDeleted(e.getDeleted());
        t.setCreateTime(e.getCreateTime());
        t.setUpdateTime(e.getUpdateTime());
        return t;
    }

    /**
     * Domain → Entity。
     * <p>枚举序列化为字符串列；值对象经 fastjson2 序列化为 JSON 列；Boolean 代理开关转 0/1；
     * deleted 为 null 时兜底 0（NOT NULL 列约束）。
     *
     * @param t 领域聚合根
     * @return MyBatis 持久化实体
     */
    public static ToolEntity fromDomain(Tool t) {
        ToolEntity e = new ToolEntity();
        e.setId(t.getId());
        e.setNum(t.getNum());
        e.setWorkspaceNum(t.getWorkspaceNum());
        e.setName(t.getName());
        e.setDescription(t.getDescription());
        e.setType(t.getType() == null ? null : t.getType().name());
        e.setCreationMode(t.getCreationMode() == null ? null : t.getCreationMode().name());
        e.setTags(t.getTags() == null ? null : JSON.toJSONString(t.getTags()));
        e.setStatus(t.getStatus() == null ? null : t.getStatus().name());
        e.setMcpConfigType(t.getMcpConfigType() == null ? null : t.getMcpConfigType().name());
        e.setMcpConfig(t.getMcpConfig());
        e.setProxyEnabled(Boolean.TRUE.equals(t.getProxyEnabled()) ? 1 : 0);
        e.setProxyHeaders(t.getProxyHeaders() == null ? null : JSON.toJSONString(t.getProxyHeaders()));
        e.setPackageMode(t.getPackageMode() == null ? null : t.getPackageMode().name());
        e.setSourceFcToolNum(t.getSourceFcToolNum());
        e.setOpenApiSpec(t.getOpenApiSpec());
        e.setBaseUrl(t.getBaseUrl());
        e.setEndpoints(t.getEndpoints() == null ? null : JSON.toJSONString(t.getEndpoints()));
        e.setEndpointMeta(t.getEndpointMeta() == null ? null : JSON.toJSONString(t.getEndpointMeta()));
        e.setOwnerUserId(t.getOwnerUserId());
        e.setCreateNo(t.getCreateNo());
        e.setUpdateNo(t.getUpdateNo());
        e.setDeleted(t.getDeleted() == null ? 0 : t.getDeleted());
        e.setCreateTime(t.getCreateTime());
        e.setUpdateTime(t.getUpdateTime());
        return e;
    }
}
