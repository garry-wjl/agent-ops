package ink.garry.rd.agent.ws.infra.common.config;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置。
 * <p>
 * 阿里云 Redis 6+ 默认有多账号 ACL，{@code app.redis.username} 非空时走
 * {@code AUTH USER PASS}（命中指定账号），为空时走 {@code AUTH PASS}（命中 default 账号）。
 * 自建 Redis / 本地 dev 通常 username 为空。
 */
@Configuration
public class RedissonConfig {

    /**
     * 构建 Redisson 单机模式客户端。
     *
     * @param host     Redis host
     * @param port     Redis 端口
     * @param username Redis ACL 账号；为空走默认账号
     * @param password Redis 密码；为空表示无密码
     * @param database Redis DB 序号
     * @param ssl      是否启用 SSL/TLS
     * @return {@link RedissonClient}，容器关闭时自动 shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${app.redis.host:127.0.0.1}") String host,
            @Value("${app.redis.port:6379}") int port,
            @Value("${app.redis.username:}") String username,
            @Value("${app.redis.password:}") String password,
            @Value("${app.redis.database:0}") int database,
            @Value("${app.redis.ssl:false}") boolean ssl) {
        Config cfg = new Config();
        String scheme = ssl ? "rediss://" : "redis://";
        SingleServerConfig single = cfg.useSingleServer()
                .setAddress(scheme + host + ":" + port)
                .setPassword(password == null || password.isEmpty() ? null : password)
                .setDatabase(database);
        if (ssl) {
            single.setSslEnableEndpointIdentification(false);
            single.setSslTruststore(null);
            single.setSslTrustManagerFactory(InsecureTrustManagerFactory.INSTANCE);
        }
        if (username != null && !username.isEmpty()) {
            single.setUsername(username);
        }
        return Redisson.create(cfg);
    }
}
