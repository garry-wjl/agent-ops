package ink.garry.rd.agent.ws.domain.sandbox.dto;

import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 沙箱领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.SANDBOX_*} 事件类型解码使用。其中
 * {@code SANDBOX_SUBMITTED} 由 {@code SandboxDomainEventListener} 消费触发异步供给；
 * {@code SANDBOX_PROVISION_FAILED} 经 {@link #failReason} 携带失败原因。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SandboxDomainEventDTO {

    /** 沙箱业务编号（SBX-...）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 沙箱名称（事件发生时的快照）。 */
    private String name;

    /** 状态（事件发生时的快照）。 */
    private String status;

    /** CPU 核数快照。 */
    private BigDecimal cpu;

    /** 内存大小（MB）快照。 */
    private Integer memoryMb;

    /** 容器存活时间（分钟）快照。 */
    private Integer aliveMinutes;

    /** OpenSandbox 容器实例 id 快照（草稿 / 失败态可能为空）。 */
    private String sandboxInstanceId;

    /** 失败原因（仅 SANDBOX_PROVISION_FAILED 事件填充）。 */
    private String failReason;

    /** 操作人工号（用于审计与事件链路追溯）。 */
    private String operatorEmpNo;

    /** 事件实际发生时间。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Sandbox 聚合根快照构造事件载荷（不含失败原因）。
     *
     * @param sandbox    Sandbox 聚合根
     * @param operatorId 操作人工号
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static SandboxDomainEventDTO from(Sandbox sandbox, String operatorId) {
        return from(sandbox, operatorId, null);
    }

    /**
     * 从 Sandbox 聚合根快照构造事件载荷（含失败原因）。
     *
     * @param sandbox    Sandbox 聚合根
     * @param operatorId 操作人工号
     * @param failReason 失败原因（非失败事件传 null）
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static SandboxDomainEventDTO from(Sandbox sandbox, String operatorId, String failReason) {
        return SandboxDomainEventDTO.builder()
                .num(sandbox.getNum())
                .workspaceNum(sandbox.getWorkspaceNum())
                .name(sandbox.getName())
                .status(sandbox.getStatus() == null ? null : sandbox.getStatus().name())
                .cpu(sandbox.getCpu())
                .memoryMb(sandbox.getMemoryMb())
                .aliveMinutes(sandbox.getAliveMinutes())
                .sandboxInstanceId(sandbox.getSandboxInstanceId())
                .failReason(failReason)
                .operatorEmpNo(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
