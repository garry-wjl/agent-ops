package ink.garry.rd.agent.ws.infra.agentscope.state;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.state.VersionedState;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 为 Redis {@link AgentStateStore} 增加会话级滑动 TTL 的装饰器。
 *
 * <p>AgentScope 自带 Redis 状态存储写入时不设过期；本装饰器在每次读写后，
 * 将该 {@code (userId, sessionId)} 槽位下相关 key 续期为配置的 TTL（默认 15 天）。
 */
@Slf4j
public class TtlRenewingAgentStateStore implements AgentStateStore {

    /** 与 AgentScope Redis 默认前缀一致。 */
    public static final String DEFAULT_KEY_PREFIX = "agentscope:session:";

    private static final String ANON_USER = "__anon__";
    private static final String KEYS_SUFFIX = ":_keys";
    private static final String LIST_SUFFIX = ":list";
    private static final String VERSION_SUFFIX = ":ver";

    /** 装配依赖：底层 Redis 状态存储。 */
    private final AgentStateStore delegate;
    /** 装配依赖：用于 EXPIRE 的 Redisson 客户端。 */
    private final RedissonClient redissonClient;
    /** Redis key 前缀，须与底层 store 一致。 */
    private final String keyPrefix;
    /** 会话状态滑动 TTL；null / 非正表示不过期。 */
    private final Duration ttl;

    /**
     * @param delegate       底层 AgentStateStore
     * @param redissonClient Redisson 客户端
     * @param keyPrefix      Redis key 前缀
     * @param ttl            滑动过期时长
     */
    public TtlRenewingAgentStateStore(AgentStateStore delegate,
                                      RedissonClient redissonClient,
                                      String keyPrefix,
                                      Duration ttl) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (redissonClient == null) {
            throw new IllegalArgumentException("redissonClient must not be null");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        this.delegate = delegate;
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public boolean supportsVersioning() {
        return delegate.supportsVersioning();
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        delegate.save(userId, sessionId, key, value);
        renewTtl(userId, sessionId);
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        delegate.save(userId, sessionId, key, values);
        renewTtl(userId, sessionId);
    }

    @Override
    public long saveIfVersion(String userId, String sessionId, String key, State value, long expectedVersion) {
        long ver = delegate.saveIfVersion(userId, sessionId, key, value, expectedVersion);
        renewTtl(userId, sessionId);
        return ver;
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        Optional<T> result = delegate.get(userId, sessionId, key, type);
        renewTtl(userId, sessionId);
        return result;
    }

    @Override
    public <T extends State> VersionedState<T> getVersioned(
            String userId, String sessionId, String key, Class<T> type) {
        VersionedState<T> result = delegate.getVersioned(userId, sessionId, key, type);
        renewTtl(userId, sessionId);
        return result;
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        List<T> result = delegate.getList(userId, sessionId, key, itemType);
        renewTtl(userId, sessionId);
        return result;
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        boolean result = delegate.exists(userId, sessionId);
        if (result) {
            renewTtl(userId, sessionId);
        }
        return result;
    }

    @Override
    public void delete(String userId, String sessionId) {
        delegate.delete(userId, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        delegate.delete(userId, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return delegate.listSessionIds(userId);
    }

    @Override
    public void close() {
        // 底层 store / RedissonClient 由 Spring 生命周期管理，此处不关闭。
    }

    /**
     * 将指定会话槽位下的 Redis key 续期为配置 TTL（滑动过期）。
     *
     * @param userId    用户 ID，可空（匿名）
     * @param sessionId 会话 ID
     */
    void renewTtl(String userId, String sessionId) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String slotId = normalizeUser(userId) + "/" + sessionId;
        try {
            Set<String> keysToExpire = collectSessionKeys(slotId);
            if (keysToExpire.isEmpty()) {
                return;
            }
            RKeys rKeys = redissonClient.getKeys();
            // Redisson 4: expire(Duration, String...) 批量续期
            rKeys.expire(ttl, keysToExpire.toArray(new String[0]));
        } catch (Exception e) {
            log.warn("[agent-state-ttl] renew failed slotId={}, ttl={}, reason={}",
                    slotId, ttl, e.toString());
        }
    }

    /**
     * 收集会话槽位下应续期的 key：{@code _keys} 集合本身 + 其成员对应的 payload / list / ver。
     *
     * @param slotId {@code userSegment/sessionId}
     * @return 待 EXPIRE 的 Redis key 集合
     */
    Set<String> collectSessionKeys(String slotId) {
        Set<String> out = new HashSet<>();
        String keysKey = keyPrefix + slotId + KEYS_SUFFIX;
        out.add(keysKey);

        RSet<String> tracked = redissonClient.getSet(keysKey, StringCodec.INSTANCE);
        Set<String> members = tracked.isExists() ? tracked.readAll() : Set.of();
        if (members != null) {
            for (String member : members) {
                if (member == null || member.isBlank()) {
                    continue;
                }
                if (member.endsWith(LIST_SUFFIX)) {
                    String base = member.substring(0, member.length() - LIST_SUFFIX.length());
                    out.add(keyPrefix + slotId + ":" + base + LIST_SUFFIX);
                } else {
                    String stateKey = keyPrefix + slotId + ":" + member;
                    out.add(stateKey);
                    out.add(stateKey + VERSION_SUFFIX);
                }
            }
        }

        // 兜底：扫描前缀，覆盖未登记进 _keys 的历史 key
        String pattern = keyPrefix + slotId + "*";
        KeysScanOptions options = KeysScanOptions.defaults().pattern(pattern);
        for (String key : redissonClient.getKeys().getKeys(options)) {
            out.add(key);
        }
        return out;
    }

    private static String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? ANON_USER : userId;
    }
}
