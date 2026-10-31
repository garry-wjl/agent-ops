package ink.garry.rd.agent.ws.infra.agentscope.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * {@link TtlRenewingAgentStateStore} 单元测试：TTL 关闭时不碰 Redis；开启时续期。
 */
class TtlRenewingAgentStateStoreTest {

    @Test
    @DisplayName("ttl=0 时 save 只委托底层，不续期")
    void save_whenTtlDisabled_doesNotExpire() {
        AgentStateStore delegate = mock(AgentStateStore.class);
        RedissonClient redisson = mock(RedissonClient.class);
        TtlRenewingAgentStateStore store = new TtlRenewingAgentStateStore(
                delegate, redisson, TtlRenewingAgentStateStore.DEFAULT_KEY_PREFIX, Duration.ZERO);

        State state = mock(State.class);
        store.save("u1", "s1", "agent_state", state);

        verify(delegate).save("u1", "s1", "agent_state", state);
        verify(redisson, never()).getKeys();
    }

    @Test
    @DisplayName("正 TTL 时 save 后对会话 key 调用 expire")
    @SuppressWarnings("unchecked")
    void save_whenTtlEnabled_expiresSessionKeys() {
        AgentStateStore delegate = mock(AgentStateStore.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RKeys rKeys = mock(RKeys.class);
        RSet<String> keySet = mock(RSet.class);
        when(redisson.getKeys()).thenReturn(rKeys);
        doReturn(keySet).when(redisson).getSet(anyString(), any(Codec.class));
        when(keySet.isExists()).thenReturn(true);
        when(keySet.readAll()).thenReturn(Set.of("agent_state"));
        when(rKeys.getKeys(any())).thenReturn(Collections.emptyList());

        Duration ttl = Duration.ofDays(15);
        TtlRenewingAgentStateStore store = new TtlRenewingAgentStateStore(
                delegate, redisson, TtlRenewingAgentStateStore.DEFAULT_KEY_PREFIX, ttl);

        store.save("u1", "s1", "agent_state", mock(State.class));

        verify(rKeys).expire(eq(ttl), any(String[].class));
    }

    @Test
    @DisplayName("get 命中后同样滑动续期")
    @SuppressWarnings("unchecked")
    void get_renewsTtl() {
        AgentStateStore delegate = mock(AgentStateStore.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RKeys rKeys = mock(RKeys.class);
        RSet<String> keySet = mock(RSet.class);
        when(redisson.getKeys()).thenReturn(rKeys);
        doReturn(keySet).when(redisson).getSet(anyString(), any(Codec.class));
        when(keySet.isExists()).thenReturn(false);
        when(rKeys.getKeys(any())).thenReturn(Collections.emptyList());
        when(delegate.get(anyString(), anyString(), anyString(), any())).thenReturn(Optional.empty());

        Duration ttl = Duration.ofDays(15);
        TtlRenewingAgentStateStore store = new TtlRenewingAgentStateStore(
                delegate, redisson, TtlRenewingAgentStateStore.DEFAULT_KEY_PREFIX, ttl);

        store.get("u1", "s1", "agent_state", State.class);

        verify(rKeys).expire(eq(ttl), any(String[].class));
    }

    @Test
    @DisplayName("默认配置语义：15 天")
    void defaultTtlIsFifteenDays() {
        assertEquals(15L, Duration.ofDays(15).toDays());
        assertTrue(Duration.ofDays(15).compareTo(Duration.ofDays(14)) > 0);
    }
}
