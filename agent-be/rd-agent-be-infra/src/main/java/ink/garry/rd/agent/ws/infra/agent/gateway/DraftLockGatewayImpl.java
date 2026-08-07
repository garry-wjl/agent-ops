package ink.garry.rd.agent.ws.infra.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.gateway.DraftLockGateway;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * {@link DraftLockGateway} 实现：Redisson 分布式锁 + RBucket 记录持有者。
 * <p>
 * v3.0：原 {@code AgentDraftLockGatewayImpl} 重命名而来；锁 key / holder key 前缀保留
 * {@code agent:draft:*}，对存量 Redis 数据兼容。锁前缀已收敛到
 * {@link LockKeyConstant#AGENT_DRAFT_LOCK_PREFIX}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftLockGatewayImpl implements DraftLockGateway {

    /** Redisson 持有者 RBucket key 前缀（按 agentNum 维度） */
    private static final String HOLDER_KEY_PREFIX = "agent:draft:holder:";

    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String agentNum, String editorId, long ttlSeconds) {
        RLock lock = redissonClient.getLock(LockKeyConstant.AGENT_DRAFT_LOCK_PREFIX + agentNum);
        try {
            boolean ok = lock.tryLock(0, ttlSeconds, TimeUnit.SECONDS);
            if (ok) {
                RBucket<String> holder = redissonClient.getBucket(HOLDER_KEY_PREFIX + agentNum);
                holder.set(editorId, ttlSeconds, TimeUnit.SECONDS);
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("draft lock interrupted agentNum={} editorId={}", agentNum, editorId);
            return false;
        }
    }

    @Override
    public void unlock(String agentNum, String editorId) {
        RBucket<String> holder = redissonClient.getBucket(HOLDER_KEY_PREFIX + agentNum);
        String current = holder.get();
        if (current == null || !current.equals(editorId)) {
            return;
        }
        RLock lock = redissonClient.getLock(LockKeyConstant.AGENT_DRAFT_LOCK_PREFIX + agentNum);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        } else {
            lock.forceUnlock();
        }
        holder.delete();
    }

    @Override
    public String currentHolder(String agentNum) {
        RBucket<String> holder = redissonClient.getBucket(HOLDER_KEY_PREFIX + agentNum);
        return holder.get();
    }
}
