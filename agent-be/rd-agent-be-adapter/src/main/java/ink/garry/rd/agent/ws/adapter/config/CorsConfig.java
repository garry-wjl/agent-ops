package ink.garry.rd.agent.ws.adapter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置。
 * <p>
 * Cookie + JWT 模式要求 {@code allowCredentials=true}；按浏览器规范，
 * 此时 {@code allowedOrigins} 不能用 {@code "*"}，必须显式列出 origin。
 * <p>
 * 通过 {@code app.cors.allowed-origins}（逗号分隔）注入；缺省给本地开发态
 * 三个常见 origin。生产 / 预发必须在 application-prod.yml / application-stag.yml
 * 显式覆盖为业务域名（例如 {@code https://agent.garrycorp.com}）。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
