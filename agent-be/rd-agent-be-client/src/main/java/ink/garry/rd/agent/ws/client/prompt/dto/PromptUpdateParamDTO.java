package ink.garry.rd.agent.ws.client.prompt.dto;

import lombok.Data;

import java.util.List;

/**
 * 编辑 Prompt 入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>
 * 编辑即生效（Prompt 无状态机）。num 定位被编辑 Prompt；其余字段为空表示不修改对应项
 * （由应用层按非空判定增量赋值）。
 */
@Data
public class PromptUpdateParamDTO {

    /** Prompt 业务编号（必填，定位被编辑 Prompt）。 */
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
