package ink.garry.rd.agent.ws.client.auth.role.vo;

import lombok.Data;

/**
 * 全平台角色总览 VO（platform_admin 在 {@code listAll} 接口拿到所有空间的角色）。
 */
@Data
public class RoleSummaryVO {

    /** 角色业务编号 */
    private String roleNum;

    /** 角色名 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 作用域：PLATFORM / SPACE */
    private String scope;

    /** 归属空间编号（scope=SPACE 时） */
    private String workspaceNum;

    /** 归属空间名称（scope=SPACE 时，便于平台总览展示） */
    private String workspaceName;

    /** 是否内置 */
    private Boolean builtin;

    /** 绑定用户数 */
    private Long assignedUserCount;

    /** 拥有权限数量 */
    private Integer permissionCount;
}
