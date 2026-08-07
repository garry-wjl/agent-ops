package ink.garry.rd.agent.ws.domain.workspace.dto;

import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作空间领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.WORKSPACE_*} 事件类型解码使用。成员的增删与角色变化由
 * {@code WORKSPACE_UPDATED} 承载完整 adminEmpNos / memberEmpNos 快照（不拆细粒度成员事件，
 * 与「只有 save / delete 两个领域动作」一致）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceDomainEventDTO {

    /** 工作空间业务编号（WS-...）。 */
    private String num;

    /** 空间名称（事件发生时的快照）。 */
    private String name;

    /** 创建人工号。 */
    private String creatorEmpNo;

    /** 管理员工号数组快照。 */
    private List<String> adminEmpNos;

    /** 普通成员工号数组快照。 */
    private List<String> memberEmpNos;

    /** 操作人工号（用于审计与事件链路追溯）。 */
    private String operatorEmpNo;

    /** 事件实际发生时间。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Workspace 聚合根快照构造事件载荷。
     *
     * @param workspace  Workspace 聚合根
     * @param operatorId 操作人工号
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static WorkspaceDomainEventDTO from(Workspace workspace, String operatorId) {
        return WorkspaceDomainEventDTO.builder()
                .num(workspace.getNum())
                .name(workspace.getName())
                .creatorEmpNo(workspace.getCreateNo())
                .adminEmpNos(workspace.getAdminList() == null
                        ? new ArrayList<>() : new ArrayList<>(workspace.getAdminList()))
                .memberEmpNos(workspace.getMemberList() == null
                        ? new ArrayList<>() : new ArrayList<>(workspace.getMemberList()))
                .operatorEmpNo(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
