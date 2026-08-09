package ink.garry.rd.agent.ws.client.user.constant;

/**
 * 用户管理域常量。
 */
public final class UserConstants {

    private UserConstants() {}

    /** 业务编号前缀 */
    public static final String NUM_PREFIX = "USR-";

    /** 用户名最大长度 */
    public static final int USERNAME_MAX_LENGTH = 64;

    /** 邮箱最大长度 */
    public static final int EMAIL_MAX_LENGTH = 128;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 512;

    /** 密码最小长度 */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** 密码最大长度 */
    public static final int PASSWORD_MAX_LENGTH = 64;

    /** 用户名合法字符正则 */
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]+$";

    /** 启用 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 禁用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 选人搜索默认条数上限 */
    public static final int SEARCH_DEFAULT_LIMIT = 20;

    /** 选人搜索最大条数上限 */
    public static final int SEARCH_MAX_LIMIT = 50;
}
