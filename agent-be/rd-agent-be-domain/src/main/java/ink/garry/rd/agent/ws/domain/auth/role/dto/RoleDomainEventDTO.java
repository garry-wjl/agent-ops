package ink.garry.rd.agent.ws.domain.auth.role.dto;

import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Role 领域事件载荷 POJO。
 * 承载 ROLE_CREATED / ROLE_UPDATED / ROLE_DELETED 三个事件类型的载荷。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleDomainEventDTO {

    /** 角色业务编号 */
    private String roleNum;

    /** 角色名 */
    private String name;

    /** 作用域 */
    private RoleScope scope;

    /** 归属空间编号（scope=PLATFORM 时为 null） */
    private String workspaceNum;

    /** 权限码集合快照 */
    private List<String> permissionCodes;

    /** 操作人工号 */
    private String operatorEmpNo;

    /** 事件发生时刻 */
    private LocalDateTime occurredAt;

    /**
     * 从 Role 聚合根快照构造事件载荷。
     *
     * @param role       Role 聚合根
     * @param operatorId 操作人工号
     * @return 已填充字段的事件载荷
     */
    public static RoleDomainEventDTO from(Role role, String operatorId) {
        Set<String> codes = role.getPermissionCodes();
        return RoleDomainEventDTO.builder()
                .roleNum(role.getNum())
                .name(role.getName())
                .scope(role.getScope())
                .workspaceNum(role.getWorkspaceNum())
                .permissionCodes(codes == null ? new ArrayList<>() : new ArrayList<>(codes))
                .operatorEmpNo(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
