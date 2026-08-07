package ink.garry.rd.agent.ws.facade.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 领域事件载荷（跨模块共享）。
 * 作为 {@link ink.garry.rd.agent.ws.facade.domain.DomainEventDTO#getData()} 的具体业务数据，
 * 由 Agent 聚合在创建 / 更新 / 发布 / 删除等关键节点发布。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDomainEventDTO {
    /** Agent 业务编号 */
    private String agentNum;
    /** Agent 版本编号（如 vX.Y.Z 或对应 versionNum） */
    private String versionNum;
    /** 变更级别（PATCH / MINOR / MAJOR） */
    private String changeLevel;
    /** 操作人ID */
    private String operatorId;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
}
