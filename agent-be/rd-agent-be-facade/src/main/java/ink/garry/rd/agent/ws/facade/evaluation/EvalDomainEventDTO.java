package ink.garry.rd.agent.ws.facade.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Evaluation 领域事件载荷。
 * 作为 {@link ink.garry.rd.agent.ws.facade.domain.DomainEventDTO#getData()} 的具体业务数据，
 * 由 Evaluation 聚合在创建 / 运行 / 完成 / 失败等关键节点发布。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalDomainEventDTO {
    /** 评测业务编号 */
    private String evaluationNum;
    /** 被评测的 Agent 业务编号 */
    private String agentNum;
    /** 被评测的 Agent 版本编号 */
    private String agentVersionNum;
    /** 评测状态（如 PENDING / RUNNING / SUCCEEDED / FAILED） */
    private String status;
    /** 操作人ID */
    private String operatorId;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
}
