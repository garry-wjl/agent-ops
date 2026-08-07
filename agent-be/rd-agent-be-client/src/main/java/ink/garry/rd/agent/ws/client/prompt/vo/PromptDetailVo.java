package ink.garry.rd.agent.ws.client.prompt.vo;

import lombok.Data;

/**
 * Prompt 详情 Vo（adapter 层出参）。
 * <p>
 * 本期详情即 Prompt 全字段，以嵌套 {@link PromptVo} 承载；
 * 由 application 的 {@code PromptDetailDTO} 经 {@code PromptVoAssembler} 转换而来。
 */
@Data
public class PromptDetailVo {

    /** Prompt 全字段快照。 */
    private PromptVo prompt;
}
