package ink.garry.rd.agent.ws.domain.auth;

/**
 * 用户角色绑定类型。
 * <p>PLATFORM：平台角色绑定，workspaceNum 固定为 {@code SYSTEM}；</p>
 * <p>SPACE：空间角色绑定，workspaceNum 为具体工作空间编号。</p>
 */
public enum RoleBindingType {
    /** 平台角色绑定 */
    PLATFORM,
    /** 空间角色绑定 */
    SPACE
}
