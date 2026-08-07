package ink.garry.rd.agent.ws.client.auth.role.param;

import lombok.Data;

import java.util.List;

/**
 * 创建空间自定义角色入参。
 * 由 {@code RoleCommandController.createRole} 接收；scope 固定 SPACE，workspaceNum 取自 {@code WorkspaceContextHolder}。
 */
@Data
public class RoleCreateParam {

    /** 角色名（必填，1~64 字符，同空间内唯一） */
    private String name;

    /** 角色描述（可空，≤200 字符） */
    private String description;

    /** 权限码集合（必填，全部必须存在于 PermissionRegistry） */
    private List<String> permissionCodes;
}
