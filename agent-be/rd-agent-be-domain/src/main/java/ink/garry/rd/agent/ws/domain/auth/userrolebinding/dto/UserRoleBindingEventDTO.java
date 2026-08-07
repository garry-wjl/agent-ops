package ink.garry.rd.agent.ws.domain.auth.userrolebinding.dto;

import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
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
 * UserRoleBinding 领域事件载荷。
 * 承载 USER_ROLE_BOUND / USER_ROLE_UNBOUND 两个事件。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleBindingEventDTO {

    /** 业务编码（UR-PLATFORM-{userId} / UR-SPACE-{wsNum}-{userId}） */
    private String num;

    /** 工作空间业务编号（SYSTEM 表示平台角色） */
    private String workspaceNum;

    /** 绑定类型 */
    private RoleBindingType roleType;

    /** 用户工号 */
    private String userId;

    /** 当前角色 num 集合（unbind 事件时即被清空前的集合） */
    private List<String> roleNums;

    /** 操作人工号 */
    private String operatorEmpNo;

    /** 事件发生时间 */
    private LocalDateTime occurredAt;

    /** 从聚合根快照构造事件载荷。 */
    public static UserRoleBindingEventDTO from(UserRoleBinding binding, String operatorId) {
        Set<String> codes = binding.getRoleNums();
        return UserRoleBindingEventDTO.builder()
                .num(binding.getNum())
                .workspaceNum(binding.getWorkspaceNum())
                .roleType(binding.getRoleType())
                .userId(binding.getUserId())
                .roleNums(codes == null ? new ArrayList<>() : new ArrayList<>(codes))
                .operatorEmpNo(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
