package ink.garry.rd.agent.ws.infra.auth;

import ink.garry.rd.agent.ws.infra.auth.token.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 认证模块 infra 层配置入口。
 * <p>
 * 集中启用本模块的 {@code @ConfigurationProperties} 绑定，避免散落在各
 * Bean 注解上而难以维护。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class})
public class AuthInfraConfig {
}
