package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具列表分页查询入参 DTO（application 层用）。
 * <p>所有筛选字段均为可选；为空表示不过滤。workspaceNum 由 adapter 从当前空间上下文传入。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolPageQueryParamDTO {

    /** 当前页码，从 1 起。 */
    private Integer pageNo;

    /** 每页大小（默认 20，最大 100）。 */
    private Integer pageSize;

    /** 按类型筛选：MCP / FUNCTION_CALL；null 表示不限。 */
    private String type;

    /** 按创建方式筛选：REMOTE / API_PACKAGE / OPENAPI_SPEC / MANUAL；null 表示不限。 */
    private String creationMode;

    /** 按状态筛选：DRAFT / PUBLISHED / DEPRECATED；null 表示不限。 */
    private String status;

    /** 按单个标签筛选；null/空表示不限。 */
    private String tag;

    /** 关键词（在 num / name / description 内 LIKE 匹配）；null/空表示不限。 */
    private String keyword;
}
