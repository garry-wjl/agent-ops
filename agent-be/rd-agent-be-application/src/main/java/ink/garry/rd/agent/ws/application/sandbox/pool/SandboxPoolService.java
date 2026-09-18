package ink.garry.rd.agent.ws.application.sandbox.pool;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.factory.SandboxFactory;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxContainerGateway;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRuntimeInstanceRepository;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRuntimeInstanceRepository.SandboxRuntimeInstanceRecord;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxRuntimeStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 沙箱热池 / 会话绑定服务。
 * <p>
 * 负责：供给填池、会话 ensureBound、复用、回收、补水位。远程 create/kill 经
 * {@link SandboxContainerGateway}（可 Mock）。
 */
@Slf4j
@Service
public class SandboxPoolService {

    private static final long LOCK_WAIT_SECONDS = 5L;
    private static final long LOCK_LEASE_SECONDS = 60L;
    private static final String POOL_LOCK_PREFIX = "sandbox:pool:lock:";

    @Resource
    private SandboxFactory sandboxFactory;
    @Resource
    private SandboxGateway sandboxGateway;
    @Resource
    private SandboxContainerGateway sandboxContainerGateway;
    @Resource
    private SandboxRuntimeInstanceRepository runtimeRepository;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 资产提交后的供给：开池则预创建 {@code poolSize} 台 IDLE；关池则不建容器。
     *
     * @param sandboxNum 资产编号
     * @param operatorId 操作人
     * @return 代表 instanceId（可为 null 或首台 IDLE）
     */
    public String provisionAsset(String sandboxNum, String operatorId) {
        Sandbox asset = requireOnlineCandidate(sandboxNum);
        boolean poolOn = Boolean.TRUE.equals(asset.getPoolEnabled());
        int poolSize = asset.getPoolSize() != null ? asset.getPoolSize() : 1;
        int maxConcurrent = asset.getMaxConcurrent() != null ? asset.getMaxConcurrent() : 8;
        String representative = null;
        if (poolOn) {
            int toCreate = Math.min(poolSize, maxConcurrent);
            for (int i = 0; i < toCreate; i++) {
                String id = createIdleInstance(asset, operatorId);
                if (representative == null) {
                    representative = id;
                }
            }
            log.info("[sandbox-pool] provisioned pool sandboxNum={} size={}", sandboxNum, toCreate);
        } else {
            log.info("[sandbox-pool] provision without pre-create (pool off) sandboxNum={}", sandboxNum);
        }
        return representative;
    }

    /**
     * 确保会话已绑定远程容器；同会话复用；优先 claim IDLE；否则 create（受 maxConcurrent 限制）。
     *
     * @param sandboxNum 资产编号
     * @param sessionNum 会话编号
     * @param operatorId 操作人（可空）
     * @return OpenSandbox instanceId
     */
    public String ensureBound(String sandboxNum, String sessionNum, String operatorId) {
        if (StrUtil.isBlank(sandboxNum) || StrUtil.isBlank(sessionNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "沙箱编号与会话编号不能为空");
        }
        String op = StrUtil.blankToDefault(operatorId, "system");
        RLock lock = redissonClient.getLock(POOL_LOCK_PREFIX + sandboxNum);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱池操作被中断");
        }
        if (!acquired) {
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱池繁忙，请稍后重试");
        }
        try {
            var existing = runtimeRepository.findBoundBySessionNum(sessionNum);
            if (existing.isPresent()) {
                SandboxRuntimeInstanceRecord cur = existing.get();
                if (sandboxNum.equals(cur.sandboxNum())) {
                    if (sandboxContainerGateway.isAlive(cur.opensandboxInstanceId())) {
                        touchActive(cur, op);
                        return cur.opensandboxInstanceId();
                    }
                    log.warn("[sandbox-pool] bound instance dead, rebind sessionNum={} instanceId={}",
                            sessionNum, cur.opensandboxInstanceId());
                    safeKill(cur.opensandboxInstanceId());
                    runtimeRepository.softDelete(cur.num());
                } else {
                    // 会话曾绑其他资产：释放旧绑定后重新绑定
                    releaseOrDestroy(cur, op, false);
                }
            }

            Sandbox asset = requireOnlineAsset(sandboxNum);
            // claim IDLE（跳过已死实例）
            List<SandboxRuntimeInstanceRecord> idles =
                    runtimeRepository.listBySandboxAndStatus(sandboxNum, SandboxRuntimeStatus.IDLE);
            for (SandboxRuntimeInstanceRecord idle : idles) {
                if (!sandboxContainerGateway.isAlive(idle.opensandboxInstanceId())) {
                    log.warn("[sandbox-pool] drop dead idle num={} instanceId={}",
                            idle.num(), idle.opensandboxInstanceId());
                    safeKill(idle.opensandboxInstanceId());
                    runtimeRepository.softDelete(idle.num());
                    continue;
                }
                SandboxRuntimeInstanceRecord bound = new SandboxRuntimeInstanceRecord(
                        idle.num(),
                        idle.sandboxNum(),
                        idle.workspaceNum(),
                        idle.opensandboxInstanceId(),
                        SandboxRuntimeStatus.BOUND,
                        sessionNum,
                        LocalDateTime.now(),
                        null,
                        idle.createNo(),
                        op);
                runtimeRepository.update(bound);
                replenishAsyncHint(asset, op);
                log.info("[sandbox-pool] claim idle sandboxNum={} sessionNum={} instanceId={}",
                        sandboxNum, sessionNum, bound.opensandboxInstanceId());
                return bound.opensandboxInstanceId();
            }

            long alive = runtimeRepository.countAlive(sandboxNum);
            int maxConcurrent = asset.getMaxConcurrent() != null ? asset.getMaxConcurrent() : 8;
            if (alive >= maxConcurrent) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "沙箱并发实例已达上限（" + maxConcurrent + "），请稍后重试或扩大 maxConcurrent");
            }
            String osId = sandboxContainerGateway.create(
                    asset.getCpu(), asset.getMemoryMb(), asset.getAliveMinutes());
            String sri = sandboxGateway.generateRuntimeInstanceNum();
            LocalDateTime now = LocalDateTime.now();
            runtimeRepository.insert(new SandboxRuntimeInstanceRecord(
                    sri,
                    asset.getNum(),
                    asset.getWorkspaceNum(),
                    osId,
                    SandboxRuntimeStatus.BOUND,
                    sessionNum,
                    now,
                    null,
                    op,
                    op));
            log.info("[sandbox-pool] create+bind sandboxNum={} sessionNum={} instanceId={}",
                    sandboxNum, sessionNum, osId);
            return osId;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 销毁资产下全部运行时实例（下线/删除时）。
     *
     * @param sandboxNum 资产编号
     */
    public void destroyAllForAsset(String sandboxNum) {
        List<SandboxRuntimeInstanceRecord> all = runtimeRepository.listBySandbox(sandboxNum);
        for (SandboxRuntimeInstanceRecord r : all) {
            safeKill(r.opensandboxInstanceId());
            runtimeRepository.softDelete(r.num());
        }
        log.info("[sandbox-pool] destroyed all runtime instances sandboxNum={} count={}",
                sandboxNum, all.size());
    }

    /**
     * 会话结束时解绑：开池且水位不足则回 IDLE，否则 kill。
     *
     * @param sessionNum 会话编号
     * @param operatorId 操作人
     */
    public void releaseBySession(String sessionNum, String operatorId) {
        if (StrUtil.isBlank(sessionNum)) {
            return;
        }
        var existing = runtimeRepository.findBoundBySessionNum(sessionNum);
        if (existing.isEmpty()) {
            return;
        }
        SandboxRuntimeInstanceRecord r = existing.get();
        Sandbox asset = sandboxFactory.buildSandboxByNum(r.sandboxNum());
        boolean preferIdle = asset != null && Boolean.TRUE.equals(asset.getPoolEnabled());
        releaseOrDestroy(r, StrUtil.blankToDefault(operatorId, "system"), preferIdle);
    }

    /**
     * 清理某资产下已死亡的远程实例（判活失败则 kill + 软删）。
     *
     * @param sandboxNum 资产编号
     * @return 清理条数
     */
    public int cleanupDeadInstances(String sandboxNum) {
        if (StrUtil.isBlank(sandboxNum)) {
            return 0;
        }
        int cleaned = 0;
        List<SandboxRuntimeInstanceRecord> all = runtimeRepository.listBySandbox(sandboxNum);
        for (SandboxRuntimeInstanceRecord r : all) {
            try {
                if (sandboxContainerGateway.isAlive(r.opensandboxInstanceId())) {
                    continue;
                }
                log.warn("[sandbox-pool] cleanup dead runtime num={} instanceId={} status={}",
                        r.num(), r.opensandboxInstanceId(), r.status());
                safeKill(r.opensandboxInstanceId());
                runtimeRepository.softDelete(r.num());
                cleaned++;
            } catch (Exception e) {
                log.warn("[sandbox-pool] cleanup failed num={}: {}", r.num(), e.getMessage());
            }
        }
        return cleaned;
    }

    /**
     * 回收超时未活跃的 BOUND 会话实例。
     */
    public void reclaimInactiveSessions() {
        // 使用较宽窗口：逐条按资产 TTL 判断
        LocalDateTime horizon = LocalDateTime.now().minusMinutes(1);
        List<SandboxRuntimeInstanceRecord> candidates =
                runtimeRepository.listBoundInactiveBefore(horizon);
        for (SandboxRuntimeInstanceRecord r : candidates) {
            try {
                Sandbox asset = sandboxFactory.buildSandboxByNum(r.sandboxNum());
                if (asset == null) {
                    releaseOrDestroy(r, "system", false);
                    continue;
                }
                int ttl = asset.getSessionIdleTtlMinutes() != null
                        ? asset.getSessionIdleTtlMinutes() : 10;
                LocalDateTime deadline = LocalDateTime.now().minusMinutes(ttl);
                // lastActiveAt 为空视为已过期（异常态兜底）
                if (r.lastActiveAt() == null || r.lastActiveAt().isBefore(deadline)) {
                    releaseOrDestroy(r, "system", Boolean.TRUE.equals(asset.getPoolEnabled()));
                }
            } catch (Exception e) {
                log.warn("[sandbox-pool] reclaim failed num={}: {}", r.num(), e.getMessage());
            }
        }
    }

    /**
     * 将开池资产的 IDLE 补到 poolSize。
     *
     * @param sandboxNum 资产编号
     * @param operatorId 操作人
     */
    public void replenish(String sandboxNum, String operatorId) {
        Sandbox asset = sandboxFactory.buildSandboxByNum(sandboxNum);
        if (asset == null || asset.getStatus() != SandboxStatus.ONLINE) {
            return;
        }
        if (!Boolean.TRUE.equals(asset.getPoolEnabled())) {
            return;
        }
        int poolSize = asset.getPoolSize() != null ? asset.getPoolSize() : 1;
        int maxConcurrent = asset.getMaxConcurrent() != null ? asset.getMaxConcurrent() : 8;
        long idle = runtimeRepository.countIdle(sandboxNum);
        long alive = runtimeRepository.countAlive(sandboxNum);
        while (idle < poolSize && alive < maxConcurrent) {
            createIdleInstance(asset, operatorId);
            idle++;
            alive++;
        }
    }

    private void replenishAsyncHint(Sandbox asset, String operatorId) {
        // 同步轻量补 1 台，避免引入额外线程池依赖；Scheduler 会再兜底
        if (Boolean.TRUE.equals(asset.getPoolEnabled())) {
            try {
                replenish(asset.getNum(), operatorId);
            } catch (Exception e) {
                log.warn("[sandbox-pool] replenish after claim failed: {}", e.getMessage());
            }
        }
    }

    private String createIdleInstance(Sandbox asset, String operatorId) {
        String osId = sandboxContainerGateway.create(
                asset.getCpu(), asset.getMemoryMb(), asset.getAliveMinutes());
        String sri = sandboxGateway.generateRuntimeInstanceNum();
        LocalDateTime now = LocalDateTime.now();
        runtimeRepository.insert(new SandboxRuntimeInstanceRecord(
                sri,
                asset.getNum(),
                asset.getWorkspaceNum(),
                osId,
                SandboxRuntimeStatus.IDLE,
                null,
                now,
                now,
                operatorId,
                operatorId));
        return osId;
    }

    private void releaseOrDestroy(SandboxRuntimeInstanceRecord r, String operatorId, boolean preferIdle) {
        if (preferIdle) {
            Sandbox asset = sandboxFactory.buildSandboxByNum(r.sandboxNum());
            int poolSize = asset != null && asset.getPoolSize() != null ? asset.getPoolSize() : 1;
            long idle = runtimeRepository.countIdle(r.sandboxNum());
            // 当前这条仍是 BOUND，释放后若 idle+1 <= poolSize 则回池
            if (idle < poolSize) {
                SandboxRuntimeInstanceRecord idleRec = new SandboxRuntimeInstanceRecord(
                        r.num(),
                        r.sandboxNum(),
                        r.workspaceNum(),
                        r.opensandboxInstanceId(),
                        SandboxRuntimeStatus.IDLE,
                        null,
                        r.lastActiveAt(),
                        LocalDateTime.now(),
                        r.createNo(),
                        operatorId);
                runtimeRepository.update(idleRec);
                log.info("[sandbox-pool] release to idle num={} instanceId={}",
                        r.num(), r.opensandboxInstanceId());
                return;
            }
        }
        safeKill(r.opensandboxInstanceId());
        runtimeRepository.softDelete(r.num());
        log.info("[sandbox-pool] destroy runtime num={} instanceId={}",
                r.num(), r.opensandboxInstanceId());
    }

    private void touchActive(SandboxRuntimeInstanceRecord cur, String op) {
        SandboxRuntimeInstanceRecord updated = new SandboxRuntimeInstanceRecord(
                cur.num(),
                cur.sandboxNum(),
                cur.workspaceNum(),
                cur.opensandboxInstanceId(),
                cur.status(),
                cur.sessionNum(),
                LocalDateTime.now(),
                cur.idleSince(),
                cur.createNo(),
                op);
        runtimeRepository.update(updated);
    }

    private Sandbox requireOnlineCandidate(String sandboxNum) {
        Sandbox asset = sandboxFactory.buildSandboxByNum(sandboxNum);
        if (asset == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在");
        }
        return asset;
    }

    private Sandbox requireOnlineAsset(String sandboxNum) {
        Sandbox asset = requireOnlineCandidate(sandboxNum);
        if (asset.getStatus() != SandboxStatus.ONLINE) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "沙箱未在线，无法绑定会话容器");
        }
        return asset;
    }

    private void safeKill(String instanceId) {
        if (StrUtil.isBlank(instanceId)) {
            return;
        }
        try {
            sandboxContainerGateway.kill(instanceId);
        } catch (Exception e) {
            log.warn("[sandbox-pool] kill failed instanceId={}: {}", instanceId, e.getMessage());
        }
    }
}
