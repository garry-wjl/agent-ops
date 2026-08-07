package ink.garry.rd.agent.ws.client.auth.constant;

/**
 * 鉴权 / 权限模块常量集合。
 * 跨层共享：domain 校验、application 默认值、adapter 入参合法性。
 */
public final class AuthzConstants {

    /** 平台管理员角色业务编号（全平台唯一） */
    public static final String ROLE_PLATFORM_ADMIN = "RL-PLATFORM-ADMIN";

    /**
     * 普通用户内置平台角色业务编号（全平台共享一份）。
     * <p>首次登录可自动分配；仅含 workspace 域权限，保证任何用户均可使用工作空间基础功能。</p>
     */
    public static final String ROLE_PLATFORM_USER = "RL-PLATFORM-USER";

    /** 空间管理员内置角色业务编号（全平台共享一份） */
    public static final String ROLE_SPACE_ADMIN = "RL-SPACE-ADMIN";

    /** 空间成员内置角色业务编号（全平台共享一份） */
    public static final String ROLE_SPACE_MEMBER = "RL-SPACE-MEMBER";

    /** 角色名长度上限（字符数） */
    public static final int ROLE_NAME_MAX_LENGTH = 64;

    /** 角色描述长度上限（字符数） */
    public static final int ROLE_DESCRIPTION_MAX_LENGTH = 200;

    /** 单用户在单个空间内可持有角色数量上限 */
    public static final int USER_ROLE_PER_WORKSPACE_LIMIT = 5;

    /** 平台角色绑定使用的虚拟 workspace_num（位于 user_workspace_role 表） */
    public static final String PLATFORM_WORKSPACE_NUM = "SYSTEM";

    /** 角色编号前缀 — 平台 */
    public static final String ROLE_NUM_PREFIX_PLATFORM = "RL-PLATFORM-";

    /** 角色编号前缀 — 空间 */
    public static final String ROLE_NUM_PREFIX_SPACE = "RL-SPACE-";

    private AuthzConstants() {
        // 常量类，禁止实例化
    }
}
