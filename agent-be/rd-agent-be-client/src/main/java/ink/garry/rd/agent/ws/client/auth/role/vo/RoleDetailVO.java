package ink.garry.rd.agent.ws.client.auth.role.vo;

import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionGroupVO;
import lombok.Data;

import java.util.List;

/**
 * 角色权限明细 VO（编辑/查看抽屉用，按资源域分组展示权限）。
 */
@Data
public class RoleDetailVO {

    /** 角色业务编号 */
    private String roleNum;

    /** 角色名 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 作用域 */
    private String scope;

    /** scope=SPACE 时归属空间编号 */
    private String workspaceNum;

    /** 是否内置 */
    private Boolean builtin;

    /** 按资源域分组的权限明细（含勾选状态：已勾选的权限即此角色拥有） */
    private List<PermissionGroupVO> permissionGroups;

    /** 当前绑定用户数 */
    private Long assignedUserCount;
}
