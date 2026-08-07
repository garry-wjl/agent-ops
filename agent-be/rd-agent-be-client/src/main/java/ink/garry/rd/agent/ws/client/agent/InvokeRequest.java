package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Agent invoke 请求体（与调试台共享契约）
 * <p>
 * 见技术方案 §10.1：input 为 string|object，按 inputType 区分。
 */
@Data
public class InvokeRequest {

    /** 文本（input_type=text）或 JSON 对象（input_type=json） */
    @NotNull(message = "input 不能为空")
    private Object input;

    /** "text" | "json" */
    @NotBlank(message = "input_type 不能为空")
    private String inputType;

    /** 调试 Skill 时的 skill_name（可选） */
    private String skillHint;

    /** 不传则起新会话 */
    private String sessionNum;
}
