package ink.garry.rd.agent.ws.domain.evaluation.task.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 用例×评估器得分值对象（贫血）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalItemScore {
    /** 评估器编号 */
    private String graderNum;
    /** 评估器版本快照 */
    private Integer graderVersion;
    /** 分数 */
    private BigDecimal score;
    /** 是否通过 */
    private Boolean passed;
    /** 说明 */
    private String explanation;
}
