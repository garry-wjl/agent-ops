package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建 CODE 评估器参数。 */
@Data
public class CreateCodeGraderParam {
    @NotBlank
    private String name;
    private String description;
    /** SpEL 表达式，返回 boolean 或 Number */
    @NotBlank
    private String script;
    /** 可选超时毫秒 */
    private Integer timeoutMs;
    /** Number 结果时的通过阈值，默认 0.5 */
    private java.math.BigDecimal passThreshold;
}
