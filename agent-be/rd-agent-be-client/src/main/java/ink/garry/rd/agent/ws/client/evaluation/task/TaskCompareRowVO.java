package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

/** 对比逐行结果：uplift/regress/same。 */
@Data
public class TaskCompareRowVO {
    private Integer rowIndex;
    private Boolean leftPass;
    private Boolean rightPass;
    /** uplift / regress / same / missing */
    private String verdict;
}
