package ink.garry.rd.agent.ws.client.evaluation.grader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 试跑结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraderTrialResultVO {
    private BigDecimal score;
    private Boolean passed;
    private String explanation;
}
