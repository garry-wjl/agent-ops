package ink.garry.rd.agent.ws.domain.auth;

/**
 * 角色作用域枚举。
 * <p>PLATFORM：平台级（如 platform_admin），workspaceNum 为 NULL；</p>
 * <p>SPACE：空间级（内置 admin/member + 空间自定义），workspaceNum 必填。</p>
 */
public enum RoleScope {
    /** 平台级角色（跨工作空间） */
    PLATFORM,
    /** 空间级角色（限于某 workspaceNum 内生效；内置 SPACE_ADMIN / SPACE_MEMBER 模板的 workspaceNum 为 NULL） */
    SPACE
}
