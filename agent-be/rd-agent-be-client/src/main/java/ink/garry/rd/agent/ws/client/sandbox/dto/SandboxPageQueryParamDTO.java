package ink.garry.rd.agent.ws.client.sandbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙箱列表分页查询入参 DTO（application 层用）。
 * <p>所有筛选字段均为可选；为空表示不过滤。workspaceNum 由 adapter 从当前空间上下文传入。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SandboxPageQueryParamDTO {

    /** 当前页码，从 1 起 */
    private Integer pageNo;

    /** 每页大小 */
    private Integer pageSize;

    /** 按类型筛选：CODE；null 表示不限 */
    private String type;

    /** 按状态筛选：DRAFT / INITIALIZED / ONLINE / OFFLINE / FAILED；null 表示不限 */
    private String status;

    /** 关键词（在 num / name / remark 内 LIKE 匹配）；null/空表示不限 */
    private String keyword;
}
