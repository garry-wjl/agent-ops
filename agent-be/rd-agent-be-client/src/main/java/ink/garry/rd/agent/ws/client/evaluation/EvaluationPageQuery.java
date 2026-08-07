package ink.garry.rd.agent.ws.client.evaluation;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评测列表分页查询入参。
 * <p>
 * 继承通用分页 {@link PageParam}（pageNo/pageSize）。所有过滤字段均可空：
 * 任一为空则视为不过滤该维度，多个非空字段按 AND 组合。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EvaluationPageQuery extends PageParam {
    /** 按 Agent 业务编号过滤；可空 */
    private String agentNum;
    /** 按 Skill 业务编号过滤；可空 */
    private String skillNum;
    /** 按评测状态过滤（如 RUNNING/COMPLETED/FAILED）；可空 */
    private String status;
}
