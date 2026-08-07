package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

/**
 * 模型详情 Vo（adapter 层出参）。
 * <p>
 * 本期详情即模型全字段 + 当前状态，以嵌套 {@link ModelVO} 承载，预留后续扩展（如调用计量）；
 * 由 application 的 {@code ModelDetailDTO} 经 {@code ModelVoAssembler} 转换而来。apiKey 仍脱敏。
 */
@Data
public class ModelDetailVO {

    /** 模型全字段快照（apiKey 脱敏）。 */
    private ModelVO model;
}
