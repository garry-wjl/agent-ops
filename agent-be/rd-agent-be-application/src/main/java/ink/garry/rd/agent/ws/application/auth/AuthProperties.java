package ink.garry.rd.agent.ws.application.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * application 层认证业务配置。
 * <p>
 * Cookie / JWT 参数由 infra 层的 {@code JwtProperties} 承载；本类只保留与业务编排相关的参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * 是否禁用鉴权（本地 dev / 联调用）；生产强制 false，CI 校验。
     * <p>
     * 启用后 JWT 校验旁路；身份优先取 {@code X-User-Id} header，缺省回落到
     * {@link #devUserId}（由 {@code UserContextFilter} 注入）。
     */
    private boolean disableAuth = false;

    /**
     * disable-auth 且未传 {@code X-User-Id} 时的本地默认用户。
     * 建议与 {@code app.auth.platform-admins} 中某账号一致，便于本地验证 RBAC。
     */
    private String devUserId = "alice.zhang";
}
