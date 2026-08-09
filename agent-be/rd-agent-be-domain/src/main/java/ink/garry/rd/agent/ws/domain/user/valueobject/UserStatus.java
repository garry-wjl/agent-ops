package ink.garry.rd.agent.ws.domain.user.valueobject;

/**
 * 用户启停状态。
 */
public enum UserStatus {

    /** 启用，可登录 */
    ENABLED,

    /** 禁用，不可登录 */
    DISABLED;

    /**
     * 解析状态字符串；非法值返回 null。
     *
     * @param value 状态字面量
     * @return 枚举或 null
     */
    public static UserStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
