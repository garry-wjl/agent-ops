package ink.garry.rd.agent.ws.domain.auth;

/**
 * 鉴权域内部常量（与 client 层 {@code AuthzConstants} 字面量保持一致，但物理隔离避免反向依赖）。
 */
public final class AuthzDomainConstants {

    /** 平台管理员角色业务编号 */
    public static final String ROLE_PLATFORM_ADMIN = "RL-PLATFORM-ADMIN";

    /**
     * 普通用户内置平台角色业务编号。
     * <p>首次 GCAC 登录自动分配；仅含 workspace 域权限，保证任何用户均可使用工作空间基础功能。</p>
     */
    public static final String ROLE_PLATFORM_USER = "RL-PLATFORM-USER";

    /** 空间管理员内置角色业务编号 */
    public static final String ROLE_SPACE_ADMIN = "RL-SPACE-ADMIN";

    /** 空间成员内置角色业务编号 */
    public static final String ROLE_SPACE_MEMBER = "RL-SPACE-MEMBER";

    /** 单用户在单个空间内可持有角色数量上限 */
    public static final int USER_ROLE_PER_WORKSPACE_LIMIT = 5;

    /** 平台角色绑定使用的虚拟 workspace_num */
    public static final String PLATFORM_WORKSPACE_NUM = "SYSTEM";

    /** 角色编号前缀 — 平台 */
    public static final String ROLE_NUM_PREFIX_PLATFORM = "RL-PLATFORM-";

    /** 角色编号前缀 — 空间 */
    public static final String ROLE_NUM_PREFIX_SPACE = "RL-SPACE-";

    private AuthzDomainConstants() {
    }
}
