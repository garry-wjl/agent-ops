package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作空间详情 DTO（编辑抽屉用，含成员列表）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceDetailDTO {

    /** 工作空间业务编号。 */
    private String num;

    /** 空间名称。 */
    private String name;

    /** 空间描述。 */
    private String description;

    /** 创建人工号。 */
    private String createNo;

    /** 当前用户在该空间内的角色（ADMIN / MEMBER）。 */
    private String myRole;

    /** 当前用户是否为创建人。 */
    private Boolean isCreator;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 成员列表（管理员 + 普通成员，含 displayName 与角色）。 */
    private List<WorkspaceMemberDTO> members;
}
