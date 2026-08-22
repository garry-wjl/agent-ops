package ink.garry.rd.agent.ws.client.evaluation.dataset;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评测集分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasetPageQuery extends PageParam {
    /** 关键字（名称/编号） */
    private String keyword;
    /** 类型过滤 */
    private String type;
    /** 状态过滤 */
    private String status;
    /** 关联 Agent 编号过滤（精确匹配） */
    private String agentNum;
}
