package ink.garry.rd.agent.ws.client.evaluation.grader;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 从预置创建内置评估器。 */
@Data
public class CreateBuiltinGraderParam {
    /** 预置编码，如 CONTAINS */
    @NotBlank
    private String presetCode;
    /** 空间内显示名 */
    @NotBlank
    private String name;
    private String description;
    /** 可选覆盖配置 JSON */
    private String configJson;
}
