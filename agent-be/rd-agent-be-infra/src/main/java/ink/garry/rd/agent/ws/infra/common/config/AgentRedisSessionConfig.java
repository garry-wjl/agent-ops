package ink.garry.rd.agent.ws.infra.common.config;

import ink.garry.rd.agent.ws.infra.agentscope.state.TtlRenewingAgentStateStore;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

import java.time.Duration;

/**
 * Agent 短期记忆（AgentState）Redis 持久化装配。
 *
 * <p>底层使用 AgentScope {@link RedisAgentStateStore}；外层套 {@link TtlRenewingAgentStateStore}
 * 做会话级滑动 TTL（默认 15 天，环境变量 {@code AGENT_STATE_TTL} 可覆盖）。
 */
@Configuration
public class AgentRedisSessionConfig {

    /** 装配依赖：共享 Redisson 客户端。 */
    @Resource
    private RedissonClient redissonClient;

    /**
     * 短期记忆滑动过期；默认 15 天。设为 {@code 0} 可关闭过期（不推荐生产）。
     * 示例：{@code AGENT_STATE_TTL=15d} / {@code 7d} / {@code 360h}。
     */
    @Value("${app.agent-state.ttl:15d}")
    private Duration agentStateTtl;

    /**
     * Redis key 前缀，须与 TTL 装饰器一致。
     */
    @Value("${app.agent-state.key-prefix:agentscope:session:}")
    private String keyPrefix;

    /**
     * @return 带滑动 TTL 的 AgentStateStore
     */
    @Bean
    public AgentStateStore buildRedisSession() {
        AgentStateStore redisStore = RedisAgentStateStore.builder()
                .redissonClient(redissonClient)
                .keyPrefix(keyPrefix)
                .build();
        return new TtlRenewingAgentStateStore(redisStore, redissonClient, keyPrefix, agentStateTtl);
    }
}
