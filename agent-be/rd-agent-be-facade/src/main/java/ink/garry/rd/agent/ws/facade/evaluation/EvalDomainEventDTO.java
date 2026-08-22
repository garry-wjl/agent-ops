package ink.garry.rd.agent.ws.facade.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评测域事件载荷（任务完成/失败等）。
 * <p>作为 DomainEventDTO#getData() 的业务数据；旧 Skill 评测字段已移除。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvalDomainEventDTO {
    /** 评测任务业务编号 */
    private String taskNum;
    /** 工作空间业务编号 */
    private String workspaceNum;
    /** 任务状态（FINISHED / FAILED / CANCELLED 等） */
    private String status;
    /** 用例总数 */
    private Integer totalCount;
    /** 综合通过数 */
    private Integer passedCount;
    /** 失败数 */
    private Integer failedCount;
    /** 操作人 ID */
    private String operatorId;
    /** 事件发生时间 */
    private LocalDateTime occurredAt;
}
