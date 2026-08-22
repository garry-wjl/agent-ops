package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

import java.util.List;

/** 任务对比结果。 */
@Data
public class TaskCompareVO {
    private String leftTaskNum;
    private String rightTaskNum;
    private Double leftPassRate;
    private Double rightPassRate;
    private Double passRateDiff;
    private List<TaskCompareRowVO> rows;
}
