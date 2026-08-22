package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

import java.util.List;

/** 评测任务用例 VO。 */
@Data
public class EvalTaskItemVO {
    private String num;
    private Integer rowIndex;
    private String inputJson;
    private String actualOutput;
    private String traceSummaryJson;
    private Boolean overallPass;
    private String status;
    private Long latencyMs;
    private String errorMessage;
    private String labelJson;
    private List<EvalTaskItemScoreVO> scores;
}
