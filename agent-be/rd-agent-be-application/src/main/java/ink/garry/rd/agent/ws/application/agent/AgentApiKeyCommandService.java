package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.Agent;
import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentApiKeyFactory;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentApiKeyGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Agent 对外调用秘钥命令服务：创建 / 删除 / 异步标记使用。
 * <p>
 * 写用例编排：通过 domain {@link AgentApiKeyFactory} 创建 / 加载秘钥实体后调用其行为，事务边界在本层。
 * <ul>
 *   <li>{@link #create}：校验 Agent 存在 + 数量 ≤50（创建锁内 count 复核防 TOCTOU）→ factory 生成
 *       key + hash/cipher/prefix → save → 解密 cipher 一次性回显明文（绝不持久化明文）；
 *       CONFIG / A2A 均可创建（A2A invoke 经 AgentRunnerFactory 透传远端）；</li>
 *   <li>{@link #delete}：校验归属匹配 → delete（逻辑删，认证立即失效）；</li>
 *   <li>{@link #touchUsedAsync}：{@code @Async} 刷新 lastUsedAt（认证过滤器调用，不阻塞 invoke 主链路）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApiKeyCommandService {

    /** 单 Agent 有效秘钥数量上限 */
    private static final int MAX_KEYS_PER_AGENT = 50;

    /** 创建锁等待 / 持有时长（秒） */
    private static final long CREATE_LOCK_WAIT_SECONDS = 3L;
    private static final long CREATE_LOCK_LEASE_SECONDS = 10L;

    /** touchUsed 异步链路操作人标识 */
    private static final String SYSTEM_OPERATOR = "system";

    private final AgentFactory agentFactory;
    private final AgentApiKeyFactory agentApiKeyFactory;
    private final AgentApiKeyGateway agentApiKeyGateway;
    private final SecretCipher secretCipher;
    private final RedissonClient redissonClient;

    /**
     * 创建秘钥并返回本次明文（仅此次内存回显，绝不持久化明文）。
     * <p>
     * 按 agentNum 加创建锁，在锁内 count 复核 ≤50，防止并发创建突破上限（TOCTOU）。
     *
     * @param agentNum   归属 Agent 业务编号
     * @param remark     用户备注（≤100）
     * @param operatorId 操作人 userId
     * @return 创建结果：秘钥 num + 本次明文 key
     */
    @Transactional
    public CreateResult create(String agentNum, String remark, String operatorId) {
        // 1. 校验 Agent 存在（CONFIG / A2A 均可创建对外调用秘钥；A2A invoke 经 AgentRunnerFactory 透传远端）
        Agent agent = agentFactory.createByNum(agentNum);
        if (agent == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "Agent 不存在 num=" + agentNum);
        }

        // 2. 创建锁内 count 复核 ≤50（防并发 TOCTOU），并生成 + 落库
        String lockKey = LockKeyConstant.AGENT_API_KEY_CREATE_LOCK_PREFIX + agentNum;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(CREATE_LOCK_WAIT_SECONDS, CREATE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "秘钥创建被中断");
        }
        if (!acquired) {
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "秘钥创建繁忙，请稍后重试");
        }
        try {
            long count = agentApiKeyGateway.countByAgent(agentNum);
            if (count >= MAX_KEYS_PER_AGENT) {
                throw new BusinessException(BizCode.API_KEY_LIMIT_EXCEEDED.getCode(),
                        "秘钥数量已达上限 " + MAX_KEYS_PER_AGENT);
            }
            // 工厂生成明文并算 hash/cipher/prefix（明文不落字段）
            AgentApiKey key = agentApiKeyFactory.create(
                    agentNum, agent.getWorkspaceNum(), remark, operatorId);
            key.save(operatorId);

            // 本次明文：cipher 可逆，解密一次性回显，绝不持久化明文
            String plain = secretCipher.decrypt(key.getKeyCipher());
            log.info("[AgentApiKey] create ok agentNum={} keyNum={} operator={}",
                    agentNum, key.getNum(), operatorId);
            return new CreateResult(key.getNum(), plain);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 删除秘钥（逻辑删，认证立即失效）；校验 num 归属 agentNum 一致。
     *
     * @param agentNum   归属 Agent 业务编号
     * @param keyNum     秘钥业务编号
     * @param operatorId 操作人 userId
     */
    @Transactional
    public void delete(String agentNum, String keyNum, String operatorId) {
        // 归属一致性校验（不匹配视为不属于该 Agent）
        if (!agentApiKeyGateway.existsByNumAndAgent(keyNum, agentNum)) {
            throw new BusinessException(BizCode.API_KEY_AGENT_MISMATCH.getCode(),
                    "秘钥与该 Agent 不匹配 agentNum=" + agentNum + " keyNum=" + keyNum);
        }
        AgentApiKey key = agentApiKeyFactory.createByNum(keyNum);
        if (key == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "秘钥不存在 num=" + keyNum);
        }
        key.delete(operatorId);
        log.info("[AgentApiKey] delete ok agentNum={} keyNum={} operator={}", agentNum, keyNum, operatorId);
    }

    /**
     * 异步刷新最近使用时间，由认证过滤器在认证成功后调用，不阻塞 invoke 主链路。
     *
     * @param keyNum 秘钥业务编号
     */
    @Async
    public void touchUsedAsync(String keyNum) {
        try {
            AgentApiKey key = agentApiKeyFactory.createByNum(keyNum);
            if (key == null) {
                return;
            }
            key.touchUsed(SYSTEM_OPERATOR);
        } catch (Exception e) {
            // 异步标记失败不影响主链路，仅告警
            log.warn("[AgentApiKey] touchUsedAsync failed keyNum={}", keyNum, e);
        }
    }

    /**
     * 创建结果载体：秘钥 num + 本次明文（仅创建响应回显一次）。
     *
     * @param num 秘钥业务编号
     * @param key 明文密钥（ak-...）
     */
    public record CreateResult(String num, String key) {}
}
