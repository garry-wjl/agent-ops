package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 创建 LLM 评估器参数。 */
@Data
public class CreateLlmGraderParam {
    @NotBlank
    private String name;
    private String description;
    /** 平台模型编号 */
    @NotBlank
    private String modelNum;
    /** 提示词模板，支持 {{var}} 占位 */
    @NotBlank
    private String promptTemplate;
    private BigDecimal scoreMin;
    private BigDecimal scoreMax;
    private BigDecimal passThreshold;
    /** 可选变量名列表（文档用途） */
    private List<String> variableNames;
}
