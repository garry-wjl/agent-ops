package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

/**
 * 评测空间统计摘要（Agent 评测页顶栏）。
 */
@Data
public class EvalStatsVO {
    /** 评测集数量 */
    private long datasetCount;
    /** 评估器数量 */
    private long graderCount;
    /** 评测任务总数 */
    private long taskCount;
    /** 运行中任务数（RUNNING） */
    private long runningTaskCount;
    /** 已完成任务数（FINISHED） */
    private long finishedTaskCount;
    /** 失败任务数（FAILED） */
    private long failedTaskCount;
    /**
     * 已完成任务的平均用例通过率（百分比 0～100）；无已完成任务时为 null。
     */
    private Double avgPassRate;
}
