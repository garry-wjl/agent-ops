package ink.garry.rd.agent.ws.client.auth.role.param;

import lombok.Data;

/**
 * 角色列表查询入参（按工作空间）。
 * 仅 platform_admin 调 {@code listAll} 不传 workspaceNum；普通查询走 {@code listInWorkspace}，workspaceNum 缺省取上下文。
 */
@Data
public class RoleQueryParam {

    /** 工作空间业务编号（可空：缺省时由 application 层从 WorkspaceContextHolder 取当前空间） */
    private String workspaceNum;
}
