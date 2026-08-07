package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工作空间卡片 / 列表项 DTO（application 层边界返回）。
 * <p>列表页与创建结果共用：含管理员 / 成员计数、当前用户角色与是否创建人。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceDTO {

    /** 工作空间业务编号（前缀 WS-）。 */
    private String num;

    /** 空间名称。 */
    private String name;

    /** 空间描述。 */
    private String description;

    /** 管理员人数。 */
    private Integer adminCount;

    /** 普通成员人数。 */
    private Integer memberCount;

    /** 当前用户在该空间内的角色（ADMIN / MEMBER）。 */
    private String myRole;

    /** 当前用户是否为创建人。 */
    private Boolean isCreator;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
