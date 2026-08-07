package ink.garry.rd.agent.ws.client.auth.roleassignment.vo;

import ink.garry.rd.agent.ws.client.auth.permission.vo.PermissionGroupVO;
import ink.garry.rd.agent.ws.client.auth.role.vo.RoleVO;
import lombok.Data;

import java.util.List;

/**
 * "我的权限" 抽屉 VO（{@code GET /api/v1/roles/my-permissions}）。
 * 综合当前用户在指定空间的角色列表 + 平台管理员标识 + 权限并集（按资源域分组）。
 */
@Data
public class MyPermissionVO {

    /** 当前用户工号 */
    private String userId;

    /** 工作空间业务编号 */
    private String workspaceNum;

    /** 是否平台管理员（true 时拥有全部 45 个权限） */
    private Boolean isPlatformAdmin;

    /** 当前用户在该空间持有的角色列表 */
    private List<RoleVO> roles;

    /** 权限并集按资源域分组（前端可折叠展示） */
    private List<PermissionGroupVO> permissionsByDomain;
}
