package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间成员 DTO（用于编辑抽屉成员列表）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceMemberDTO {

    /** 成员工号。 */
    private String empNo;

    /** 成员显示名（由通讯录解析；未接通时回退为工号）。 */
    private String displayName;

    /** 角色（ADMIN / MEMBER）。 */
    private String role;
}
