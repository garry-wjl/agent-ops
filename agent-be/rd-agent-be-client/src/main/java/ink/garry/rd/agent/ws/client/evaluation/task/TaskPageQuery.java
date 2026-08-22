package ink.garry.rd.agent.ws.client.evaluation.task;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 任务分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskPageQuery extends PageParam {
    /** 关键字（名称/编号） */
    private String keyword;
    /** 状态过滤 */
    private String status;
    /** 评测集编号过滤 */
    private String datasetNum;
    /** 关联 Agent 编号过滤（精确匹配） */
    private String agentNum;
}
