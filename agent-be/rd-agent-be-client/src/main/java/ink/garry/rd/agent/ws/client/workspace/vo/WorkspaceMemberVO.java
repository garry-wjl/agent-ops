package ink.garry.rd.agent.ws.client.workspace.vo;

import lombok.Data;

/**
 * 工作空间成员 Vo（编辑抽屉成员列表项）。
 */
@Data
public class WorkspaceMemberVO {

    /** 成员工号。 */
    private String empNo;

    /** 成员显示名。 */
    private String displayName;

    /** 角色（ADMIN / MEMBER）。 */
    private String role;
}
