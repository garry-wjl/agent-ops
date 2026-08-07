package ink.garry.rd.agent.ws.client.prompt.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 编辑 Prompt 入参 Vo（adapter 层，来自 HTTP 请求体）。
 * <p>
 * 编辑即生效（Prompt 无状态机）。num 定位被编辑 Prompt；promptKey 变更时做工作空间内唯一性预检
 * （排除自身）。其余字段为空表示不修改对应项。
 */
@Data
public class PromptUpdateParam {

    /** Prompt 业务编号（必填，定位被编辑 Prompt）。 */
    @NotBlank(message = "Prompt 业务编号不能为空")
    private String num;

    /** Prompt 引用键（≤128 字符，工作空间内唯一）。 */
    private String promptKey;

    /** 描述（≤500 字符）。 */
    private String description;

    /** 模板原文（≤20000 字符，含 {@code {{变量}}}，原样存储不解析）。 */
    private String templateContent;

    /** 标签（≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;
}
