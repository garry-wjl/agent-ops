package ink.garry.rd.agent.ws.client.tool.vo;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具列表分页查询入参 Vo（adapter 层，GET query 绑定）。
 * <p>
 * 继承通用 {@link PageParam}（pageNo / pageSize）；所有筛选字段均为可选，为空表示不过滤。
 * workspaceNum 不在此入参，由 adapter 从当前空间上下文（{@code X-Workspace-Num} 头）取得后传入 application。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolPageQueryParam extends PageParam {

    /** 按类型筛选：MCP / FUNCTION_CALL；为空表示不限。 */
    private String type;

    /** 按创建方式筛选：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL；为空表示不限。 */
    private String creationMode;

    /** 按状态筛选：DRAFT / PUBLISHED / DEPRECATED；为空表示不限。 */
    private String status;

    /** 按单个标签筛选；为空表示不限。 */
    private String tag;

    /** 关键词（在 num / name / description 内 LIKE 匹配）；为空表示不限。 */
    private String keyword;
}
