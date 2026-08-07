package ink.garry.rd.agent.ws.client.prompt.dto;

import lombok.Data;

/**
 * Prompt 列表分页查询入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>
 * 所有筛选字段均为可选，为空表示不过滤。workspaceNum 不在此 DTO，由 adapter 从当前空间上下文
 * 取得后单独作为方法参数传入 QueryService。
 */
@Data
public class PromptPageQueryParamDTO {

    /** 页码（≥1，默认 1）。 */
    private Integer pageNo;

    /** 每页条数（默认 20，上限 100）。 */
    private Integer pageSize;

    /** 按单个标签筛选；为空表示不限。 */
    private String tag;

    /** 关键词（在 num / prompt_key / description 内 LIKE 匹配）；为空表示不限。 */
    private String keyword;
}
