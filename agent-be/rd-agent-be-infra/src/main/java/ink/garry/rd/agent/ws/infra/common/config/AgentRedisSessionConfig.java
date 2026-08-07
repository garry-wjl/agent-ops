package ink.garry.rd.agent.ws.infra.common.config;

import io.agentscope.extensions.redis.state.redisson.RedissonAgentStateStore;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

@Configuration
public class AgentRedisSessionConfig {

    @Resource
    private RedissonClient redissonClient;

    @Bean
    public RedissonAgentStateStore buildRedisSession() {
        return RedissonAgentStateStore.builder()
                .redissonClient(redissonClient)
                .build();
    }

}