package ink.garry.rd.agent.ws.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型列表分页查询入参 DTO（application 层用）。
 * <p>所有筛选字段均为可选；为空表示不过滤。workspaceNum 由 adapter 从当前空间上下文传入。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelPageQueryParamDTO {

    /** 当前页码，从 1 起 */
    private Integer pageNo;

    /** 每页大小 */
    private Integer pageSize;

    /** 按模型名称精确筛选；null 表示不限 */
    private String name;

    /** 按模型标识精确筛选；null 表示不限 */
    private String modelId;

    /** 按状态筛选：DRAFT / ENABLED / DISABLED；null 表示不限 */
    private String status;

    /** 归属范围：SPACE / PLATFORM；为空时由服务端入口上下文决定 */
    private String scope;

    /** 关键词（在 num / name / model_id / remark 内 LIKE 匹配）；null/空表示不限 */
    private String keyword;
}
