package ink.garry.rd.agent.ws.domain.user.gateway;

/**
 * User 领域网关：编号生成、密码哈希、唯一性与禁用保护。
 */
public interface UserGateway {

    /**
     * 生成用户业务编号（USR- + 12hex）。
     *
     * @return 业务编号
     */
    String generateUserNum();

    /**
     * BCrypt 哈希原始密码。
     *
     * @param rawPassword 明文密码
     * @return 哈希
     */
    String hashPassword(String rawPassword);

    /**
     * 校验明文与哈希是否匹配。
     *
     * @param rawPassword 明文
     * @param passwordHash 哈希
     * @return 是否匹配
     */
    boolean matchesPassword(String rawPassword, String passwordHash);

    /**
     * 断言用户名全局唯一（排除指定 num）。
     *
     * @param username 用户名
     * @param excludeNum 排除的用户 num（可空）
     */
    void assertUsernameUnique(String username, String excludeNum);

    /**
     * 断言邮箱全局唯一（排除指定 num）。
     *
     * @param email 邮箱
     * @param excludeNum 排除的用户 num（可空）
     */
    void assertEmailUnique(String email, String excludeNum);

    /**
     * 断言该用户可被禁用（非最后一名启用态平台管理员）。
     *
     * @param userNum 用户业务编号
     */
    void assertCanDisable(String userNum);
}
