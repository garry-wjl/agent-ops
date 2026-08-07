package ink.garry.rd.agent.ws.domain.model.dto;

import ink.garry.rd.agent.ws.domain.model.Model;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.MODEL_*} 事件类型解码使用（本期无订阅者，仅保证审计一致性）。
 * <p>
 * <b>安全约束</b>：本载荷<b>严禁</b>包含 apiKey 明文 / 密文（防泄露，模型管理技术方案 §4.2.6）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelDomainEventDTO {

    /** 模型业务编号（MDL-...）。 */
    private String num;

    /** 归属范围：SPACE / PLATFORM（方案 §4.2.6 要求事件载荷携带 scope，便于下游区分系统/空间模型）。 */
    private String scope;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 用户填写的模型标识（事件发生时的快照）。 */
    private String modelId;

    /** 模型名称（事件发生时的快照）。 */
    private String name;

    /** 状态（事件发生时的快照）。 */
    private String status;

    /** 操作人工号（用于审计与事件链路追溯）。 */
    private String operatorEmpNo;

    /** 事件实际发生时间。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Model 聚合根快照构造事件载荷（不含任何密钥字段）。
     *
     * @param model      Model 聚合根
     * @param operatorId 操作人工号
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static ModelDomainEventDTO from(Model model, String operatorId) {
        return ModelDomainEventDTO.builder()
                .num(model.getNum())
                .scope(model.getScope() == null ? null : model.getScope().name())
                .workspaceNum(model.getWorkspaceNum())
                .modelId(model.getModelId())
                .name(model.getName())
                .status(model.getStatus() == null ? null : model.getStatus().name())
                .operatorEmpNo(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
