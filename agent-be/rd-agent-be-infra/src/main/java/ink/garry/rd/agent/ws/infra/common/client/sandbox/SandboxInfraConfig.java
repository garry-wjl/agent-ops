package ink.garry.rd.agent.ws.infra.common.client.sandbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 沙箱接入 infra 层配置入口。
 * <p>
 * 集中启用 {@link SandboxProperties} 的 {@code @ConfigurationProperties} 绑定，
 * 与 {@code AuthInfraConfig} 保持同一风格，避免散落在 Bean 注解上。
 */
@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class SandboxInfraConfig {
}
