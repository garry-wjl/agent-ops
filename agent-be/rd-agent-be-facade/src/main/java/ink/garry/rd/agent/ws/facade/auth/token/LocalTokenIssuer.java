package ink.garry.rd.agent.ws.facade.auth.token;

/**
 * 本地 JWT 签发与解析契约。
 * <p>
 * 实现位于 infra 层。Claims 字段固定为 {@code uuid / account / roles} 三字段，与
 * 同 SSO 域内 Go / 其它 Java 服务保持一致，便于跨服务共享 JWT。
 */
public interface LocalTokenIssuer {

    /**
     * 为指定用户签发 JWT。
     *
     * @param claims 用户身份与角色信息
     * @return HS256 签名的 JWT 字符串
     */
    String issue(UserClaims claims);

    /**
     * 校验并解析 JWT。
     *
     * @param token JWT 字符串
     * @return 解析后的 UserClaims
     * @throws ink.garry.rd.agent.ws.facade.exception.BusinessException 解析失败（签名错误 / 过期 / 篡改）
     */
    UserClaims parse(String token);
}
