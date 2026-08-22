package ink.garry.rd.agent.ws.application.evaluation.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 单评估器评分结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResult {
    private String graderNum;
    private Integer graderVersion;
    private BigDecimal score;
    private boolean passed;
    private String explanation;
}
