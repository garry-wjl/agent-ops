package ink.garry.rd.agent.ws.infra.auth.token;

import ink.garry.rd.agent.ws.facade.auth.token.LocalTokenIssuer;
import ink.garry.rd.agent.ws.facade.auth.token.UserClaims;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HS256 JWT 签发与解析实现（基于 jjwt 0.12.x）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>Secret 用 UTF-8 字节数组，便于与同 SSO 域内 Go / 其它 Java 服务互通</li>
 *   <li>Claims 固定 {@code uuid / account / roles} 三字段</li>
 *   <li>{@link SecretKey} 用 {@link AtomicReference} 缓存，监听 {@link ContextRefreshedEvent}
 *       做初始化；密钥以 YAML / 环境变量为准，变更后需重启进程</li>
 *   <li>解析失败统一抛 {@link BusinessException}({@link BizCode#UNAUTHORIZED})，
 *       由 adapter 层 Filter 转 401</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProviderImpl implements LocalTokenIssuer {

    /** BizCode {@code UNAUTHORIZED} 数字编码（与 client.common.BizCode 同步）。 */
    private static final int CODE_UNAUTHORIZED = 1002;

    private final JwtProperties props;
    private final AtomicReference<SecretKey> keyRef = new AtomicReference<>();
    private final AtomicReference<Long> expirationMsRef = new AtomicReference<>();

    /**
     * 初始化与配置热更共用的刷新方法。
     * <p>
     * Spring 容器刷新事件触发时自动调用（启动时从 YAML / 环境变量加载密钥）。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void refresh() {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.auth.jwt.secret must not be blank");
        }
        // HS256 要求 key 长度 >= 32 字节；不足时直接报错，避免线上偶发签名失败
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.auth.jwt.secret length must be >= 32 bytes (UTF-8)");
        }
        this.keyRef.set(Keys.hmacShaKeyFor(bytes));
        this.expirationMsRef.set(props.getExpirationHours() * 3600_000L);
        log.info("jwt key refreshed; expiration={}h", props.getExpirationHours());
    }

    @Override
    public String issue(UserClaims claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("uuid", claims.getUuid())
                .claim("account", claims.getAccount())
                .claim("roles", claims.getRoles())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMsRef.get()))
                .signWith(keyRef.get(), Jwts.SIG.HS256)
                .compact();
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserClaims parse(String token) {
        try {
            Claims body = Jwts.parser()
                    .verifyWith(keyRef.get())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UserClaims.builder()
                    .uuid(body.get("uuid", String.class))
                    .account(body.get("account", String.class))
                    .roles((List<String>) body.get("roles", List.class))
                    .build();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CODE_UNAUTHORIZED, "invalid token: " + e.getMessage(), e);
        }
    }
}
