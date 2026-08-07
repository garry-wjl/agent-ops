package ink.garry.rd.agent.ws.client.auth.roleassignment.param;

import lombok.Data;

/**
 * 平台角色赋人入参（仅 platform_admin 调用 {@code /api/v1/platform-roles/assign}）。
 * 本期 platformRoleNum 取值固定为 {@code RL-PLATFORM-ADMIN}。
 */
@Data
public class PlatformRoleAssignParam {

    /** 被授予平台角色的目标工号 */
    private String empNo;

    /** 平台角色业务编号 */
    private String platformRoleNum;
}
