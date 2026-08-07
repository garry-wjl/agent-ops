package ink.garry.rd.agent.ws.infra.auth.token;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地 JWT 配置。
 * <p>
 * {@link #secret} 必须与同 SSO 域内所有共享 JWT 的服务（Go / Java）保持一致；
 * 生产通过环境变量 / K8s Secret 覆盖 {@code application.yml} 占位值，勿提交真实密钥。
 */
@Data
@ConfigurationProperties(prefix = "app.auth.jwt")
public class JwtProperties {

    /** HS256 签名密钥；UTF-8 字节数组，便于跨语言互通。 */
    private String secret;

    /** JWT 过期小时数；默认 24。 */
    private long expirationHours = 24L;

    /** Cookie 名；默认 {@code session_token}。 */
    private String cookieName = "session_token";

    /** Cookie {@code Secure} 标志；跨站场景必须 true，本地 dev 可关。 */
    private boolean cookieSecure = true;

    /**
     * Cookie {@code SameSite} 策略；跨域写 Cookie 用 {@code None}，同域用 {@code Lax}。
     */
    private String cookieSameSite = "None";
}
