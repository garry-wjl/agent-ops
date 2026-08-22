package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

/**
 * 评测任务列表/详情中绑定的评估器摘要。
 */
@Data
public class EvalTaskGraderBriefVO {
    /** 评估器编号 */
    private String graderNum;
    /** 绑定快照时的评估器版本 */
    private Integer graderVersion;
    /** 评估器类型：LLM / CODE / BUILTIN */
    private String kind;
    /** 评估器名称（当前库内名称；已删除则为空，前端可回退编号） */
    private String name;
}
