package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

import java.math.BigDecimal;

/** 用例得分 VO。 */
@Data
public class EvalTaskItemScoreVO {
    private String graderNum;
    /** 评估器名称（列表展示用；已删除可为空） */
    private String graderName;
    private Integer graderVersion;
    private BigDecimal score;
    private Boolean passed;
    private String explanation;
}
