package ink.garry.rd.agent.ws.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型详情 DTO（application 层边界）。
 * <p>
 * 本期详情即模型全字段 + 当前状态，以嵌套 {@link ModelDTO} 承载，预留后续扩展（如调用计量）；
 * apiKey 仍以脱敏串呈现。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelDetailDTO {

    /** 模型全字段快照（apiKey 脱敏） */
    private ModelDTO model;
}
