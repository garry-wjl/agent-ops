package ink.garry.rd.agent.ws.infra.agent.a2a;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos AI Registry 客户端装配（v2.6 §6.2 第 2 节）。
 * <p>
 * <b>为什么需要这个</b>：starter 1.0.12 的 {@code AgentscopeA2aNacosAutoConfiguration} 把
 * {@code a2aService} 装成 private final 字段 + private 工厂方法，<b>不会</b>作为 {@link AiService}
 * bean 暴露到容器里。本服务的 {@link NacosAgentCardFetcher} 构造器强依赖 {@code AiService}，
 * 必须由本服务自己装一份 public bean。
 * <p>
 * <b>为什么不撞 bean</b>：用 {@code @ConditionalOnMissingBean(AiService.class)} 保护
 * ——若 starter 后续版本把 {@code a2aService} 升为 public bean，本类自动跳过；当前 1.0.12
 * 没有这个 bean，本类生效。
 * <p>
 * <b>激活条件</b>：仅在 {@code agentscope.a2a.nacos.discovery.enabled=true} 时激活；缺省 {@code false}，
 * 本类不生效，{@link NacosAgentCardFetcher} 也不被注册。
 * <p>
 * <b>配置复用</b>：所有字段从 AgentScope starter 的 {@code agentscope.a2a.nacos.*}
 * 取值（starter 的 {@code @ConfigurationProperties(prefix="agentscope.a2a.nacos")} 已经把
 * server-addr / namespace / access-key / secret-key / username / password 装好），
 * 避免在 {@code application.yml} 维护两份重复字段。
 * <p>
 * <b>鉴权优先级</b>（与 Nacos Java SDK 一致）：AK/SK 优先，其次用户名/密码；都为空走匿名。
 * 因此 dev 走 {@code username/password}、test 走 {@code access-key/secret-key} 都能复用本类。
 *
 * @see NacosAgentCardFetcher
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "agentscope.a2a.nacos.discovery", name = "enabled", havingValue = "true")
public class NacosAiClientConfig {

    @Value("${agentscope.a2a.nacos.server-addr:}")
    private String serverAddr;
    @Value("${agentscope.a2a.nacos.namespace:}")
    private String namespace;
    @Value("${agentscope.a2a.nacos.username:}")
    private String username;
    @Value("${agentscope.a2a.nacos.password:}")
    private String password;
    @Value("${agentscope.a2a.nacos.access-key:}")
    private String accessKey;
    @Value("${agentscope.a2a.nacos.secret-key:}")
    private String secretKey;

    /**
     * 装配 {@link AiService} bean，封装 Nacos AI Registry 客户端。
     * <p>
     * 鉴权优先级（与 Nacos Java SDK 一致）：AK/SK 优先，其次用户名/密码；都为空走匿名。
     *
     * @return AiService 实例
     * @throws NacosException Nacos 客户端初始化失败
     */
    @Bean
    @ConditionalOnMissingBean(AiService.class)
    public AiService aiService() throws NacosException {
        Properties properties = new Properties();
        if (serverAddr != null && !serverAddr.isBlank()) {
            properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        }
        if (namespace != null && !namespace.isBlank()) {
            properties.put(PropertyKeyConst.NAMESPACE, namespace);
        }
        if (accessKey != null && !accessKey.isBlank()) {
            properties.put(PropertyKeyConst.ACCESS_KEY, accessKey);
        }
        if (secretKey != null && !secretKey.isBlank()) {
            properties.put(PropertyKeyConst.SECRET_KEY, secretKey);
        }
        if (username != null && !username.isBlank()) {
            properties.put(PropertyKeyConst.USERNAME, username);
        }
        if (password != null && !password.isBlank()) {
            properties.put(PropertyKeyConst.PASSWORD, password);
        }

        log.info("[A2aFetcher] Nacos AI client 已启用 server-addr={} namespace={} auth={}",
                serverAddr, namespace,
                (accessKey != null && !accessKey.isBlank()) ? "AK/SK" : "USERNAME/PASSWORD");

        return AiFactory.createAiService(properties);
    }
}
