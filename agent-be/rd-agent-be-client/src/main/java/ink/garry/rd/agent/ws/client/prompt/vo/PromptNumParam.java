package ink.garry.rd.agent.ws.client.prompt.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Prompt 单编号操作入参 Vo（adapter 层）。
 * <p>
 * 供 delete 等仅需 Prompt 业务编号的 POST 接口使用（Prompt 中心技术方案 §7.2）。
 */
@Data
public class PromptNumParam {

    /** Prompt 业务编号（必填）。 */
    @NotBlank(message = "Prompt 业务编号不能为空")
    private String num;
}
