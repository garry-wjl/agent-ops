package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 手动新增草稿行结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddDatasetRowResultVO {
    /** 行业务编号 */
    private String rowNum;
    /** 行下标（从 0 起） */
    private Integer rowIndex;
}
