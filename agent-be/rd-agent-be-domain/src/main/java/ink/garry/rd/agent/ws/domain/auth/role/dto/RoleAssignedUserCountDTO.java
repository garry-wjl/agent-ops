package ink.garry.rd.agent.ws.domain.auth.role.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色被绑定用户数 DTO（删除前预检使用）。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleAssignedUserCountDTO {

    /** 角色业务编号 */
    private String roleNum;

    /** 当前绑定的用户数量 */
    private long count;
}
