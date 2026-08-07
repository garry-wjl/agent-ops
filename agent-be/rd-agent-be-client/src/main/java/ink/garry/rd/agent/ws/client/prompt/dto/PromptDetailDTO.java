package ink.garry.rd.agent.ws.client.prompt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt 详情 DTO（application 层边界）。
 * <p>
 * 本期详情即 Prompt 全字段，以嵌套 {@link PromptDTO} 承载；adapter 由此转 {@code PromptDetailVo}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromptDetailDTO {

    /** Prompt 全字段快照。 */
    private PromptDTO prompt;
}
