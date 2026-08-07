package ink.garry.rd.agent.ws.client.auth.role.vo;

import lombok.Data;

/**
 * 角色列表卡片 VO（{@code RoleQueryController.listInWorkspace} 用）。
 */
@Data
public class RoleVO {

    /** 角色业务编号 */
    private String roleNum;

    /** 角色名 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 作用域：PLATFORM / SPACE */
    private String scope;

    /** 是否内置 */
    private Boolean builtin;

    /** 当前角色被绑定的用户数（删除按钮禁用判定 + 列表展示） */
    private Long assignedUserCount;
}
