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
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SessionWorkspaceMount;
import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 沙箱会话绑定服务。
 * <p>
 * 热池已下线：按会话 create+bind；同会话预热与首条消息经 in-flight Future + Redis 看门狗锁合并；
 * 活动路径续期远程 TTL 并刷新 {@code lastActiveAt}。
 */
@Slf4j
@Service
public class SandboxPoolService {

    /** 会话绑定锁等待：覆盖 OpenSandbox create（默认请求超时 30s）+ 余量。 */
    private static final long SESSION_LOCK_WAIT_SECONDS = 45L;
    /** 资产容量锁等待。 */
    private static final long ASSET_LOCK_WAIT_SECONDS = 45L;
    /** 同 JVM 内 in-flight 等待上限。 */
    private static final long INFLIGHT_WAIT_SECONDS = 45L;
    /** 远程续期间隔节流。 */
    private static final long RENEW_MIN_INTERVAL_MS = 60_000L;
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
    private SessionRepository sessionRepository;
    @Resource
    private RedissonClient redissonClient;

    /** 同会话 in-flight 绑定（合并预热与 invoke）。 */
    private final ConcurrentHashMap<String, CompletableFuture<String>> inflightBySession =
            new ConcurrentHashMap<>();
    /** instanceId → 上次 renew 时间戳（毫秒）。 */
    private final ConcurrentHashMap<String, Long> lastRenewEpochMs = new ConcurrentHashMap<>();

    /**
     * 资产提交后的供给：开池则预创建；关池则不建容器。热池已下线，一律不预创建。
     *
     * @param sandboxNum 资产编号
     * @param operatorId 操作人
     * @return 代表 instanceId（恒为 null）
     */
    public String provisionAsset(String sandboxNum, String operatorId) {
        requireOnlineCandidate(sandboxNum);
        log.info("[sandbox-pool] provision without pre-create sandboxNum={} operator={}",
                sandboxNum, operatorId);
        return null;
    }

    /**
     * 确保会话已绑定远程容器；同会话复用；否则 create（受 maxConcurrent 限制）。
     *
     * @param sandboxNum 资产编号
     * @param sessionNum 会话编号
     * @param operatorId 操作人（可空）
     * @return OpenSandbox instanceId
     */
    public String ensureBound(String sandboxNum, String sessionNum, String operatorId) {
        return ensureBound(sandboxNum, sessionNum, operatorId, null);
    }

    /**
     * 绑定会话。OSS 会话隔离开启时按 {@code workspaceNum/agentNum/sessionNum} 挂卷新建。
     *
     * @param agentNum Agent 编号；隔离关闭时忽略
     * @return OpenSandbox / Docker instanceId
     */
    public String ensureBound(String sandboxNum, String sessionNum, String operatorId, String agentNum) {
        if (StrUtil.isBlank(sandboxNum) || StrUtil.isBlank(sessionNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "沙箱编号与会话编号不能为空");
        }
        String op = StrUtil.blankToDefault(operatorId, "system");
        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> existing = inflightBySession.putIfAbsent(sessionNum, created);
        if (existing != null) {
            return awaitInflight(existing, sessionNum);
        }
        try {
            String id = bindWithLocks(sandboxNum, sessionNum, op, agentNum);
            created.complete(id);
            return id;
        } catch (RuntimeException e) {
            created.completeExceptionally(e);
            throw e;
        } catch (Exception e) {
            created.completeExceptionally(e);
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(), "沙箱绑定失败: " + e.getMessage());
        } finally {
            inflightBySession.remove(sessionNum, created);
        }
    }

    /**
     * 执行期心跳：刷新 lastActiveAt 并滑动续期远程容器（节流）。
     *
     * @param sessionNum 会话编号
     */
    public void markSessionActive(String sessionNum) {
        if (StrUtil.isBlank(sessionNum)) {
            return;
        }
        var existing = runtimeRepository.findBoundBySessionNum(sessionNum);
        if (existing.isEmpty()) {
            return;
        }
        SandboxRuntimeInstanceRecord cur = existing.get();
        touchActive(cur, StrUtil.blankToDefault(cur.updateNo(), "system"));
        Sandbox asset = sandboxFactory.buildSandboxByNum(cur.sandboxNum());
        int alive = resolveAliveMinutes(asset);
        renewQuietly(cur.opensandboxInstanceId(), alive);
    }

    /**
     * 执行前确保实例可用：存活则续期；已死则按原资产重建（有限次，由调用方控制重试）。
     *
     * @param sessionNum          会话编号
     * @param preferredInstanceId 调用方持有的 instanceId（可空）
     * @param sandboxNum          资产编号（重建必需）
     * @param agentNum            Agent 编号（会话隔离挂卷时必需）
     * @param operatorId          操作人
     * @return 可用 instanceId（可能已换成新容器）
     */
    public String ensureAliveOrRebind(String sessionNum,
                                      String preferredInstanceId,
                                      String sandboxNum,
                                      String agentNum,
                                      String operatorId) {
        if (StrUtil.isBlank(sessionNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "会话编号不能为空");
        }
        var existing = runtimeRepository.findBoundBySessionNum(sessionNum);
        if (existing.isPresent()) {
            SandboxRuntimeInstanceRecord cur = existing.get();
            if (sandboxContainerGateway.isAlive(cur.opensandboxInstanceId())) {
                Sandbox asset = sandboxFactory.buildSandboxByNum(cur.sandboxNum());
                touchActive(cur, StrUtil.blankToDefault(operatorId, "system"));
                renewQuietly(cur.opensandboxInstanceId(), resolveAliveMinutes(asset));
                return cur.opensandboxInstanceId();
            }
            log.warn("[sandbox-pool] bound instance dead before exec, rebind sessionNum={} oldId={}",
                    sessionNum, cur.opensandboxInstanceId());
            safeKill(cur.opensandboxInstanceId());
            runtimeRepository.softDelete(cur.num());
            String sbx = StrUtil.blankToDefault(sandboxNum, cur.sandboxNum());
            String agt = StrUtil.blankToDefault(agentNum, lookupAgentNum(sessionNum));
            return ensureBound(sbx, sessionNum, operatorId, agt);
        }
        if (StrUtil.isBlank(sandboxNum)) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                    "会话未绑定沙箱且无法重建 sessionNum=" + sessionNum);
        }
        if (StrUtil.isNotBlank(preferredInstanceId)
                && sandboxContainerGateway.isAlive(preferredInstanceId)) {
            return preferredInstanceId;
        }
        String agt = StrUtil.blankToDefault(agentNum, lookupAgentNum(sessionNum));
        return ensureBound(sandboxNum, sessionNum, operatorId, agt);
    }

    private String lookupAgentNum(String sessionNum) {
        try {
            Session session = sessionRepository.findByNum(sessionNum);
            return session != null ? session.getAgentNum() : null;
        } catch (Exception e) {
            log.warn("[sandbox-pool] lookup agentNum failed sessionNum={}: {}",
                    sessionNum, e.getMessage());
            return null;
        }
    }

    /**
     * 当前会话已绑定的容器 id；未绑定返回 null。
     *
     * @param sessionNum 会话编号
     * @return OpenSandbox / Docker 容器 id
     */
    public String boundInstanceId(String sessionNum) {
        if (StrUtil.isBlank(sessionNum)) {
            return null;
        }
        return runtimeRepository.findBoundBySessionNum(sessionNum)
                .map(SandboxRuntimeInstanceRecord::opensandboxInstanceId)
                .orElse(null);
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
     * 会话结束时解绑：热池已下线，一律 kill。
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
        releaseOrDestroy(existing.get(), StrUtil.blankToDefault(operatorId, "system"), false);
    }

    /**
     * 清理某资产下已死亡的远程实例。
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
                if (r.lastActiveAt() == null || r.lastActiveAt().isBefore(deadline)) {
                    releaseOrDestroy(r, "system", Boolean.TRUE.equals(asset.getPoolEnabled()));
                }
            } catch (Exception e) {
                log.warn("[sandbox-pool] reclaim failed num={}: {}", r.num(), e.getMessage());
            }
        }
    }

    /**
     * 将开池资产的 IDLE 补到 poolSize（热池已下线，空实现）。
     *
     * @param sandboxNum 资产编号
     * @param operatorId 操作人
     */
    public void replenish(String sandboxNum, String operatorId) {
        // 热池已下线
    }

    private String bindWithLocks(String sandboxNum, String sessionNum, String op, String agentNum) {
        RLock sessionLock = redissonClient.getLock(
                LockKeyConstant.SANDBOX_POOL_BIND_LOCK_PREFIX + sessionNum);
        boolean sessionAcquired;
        try {
            // 看门狗：不传 leaseTime，持锁期间自动续期
            sessionAcquired = sessionLock.tryLock(SESSION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱会话绑定被中断");
        }
        if (!sessionAcquired) {
            throw new BusinessException(BizCode.CONFLICT.getCode(),
                    "沙箱正在为该会话准备中，请稍后重试");
        }
        try {
            return doEnsureBound(sandboxNum, sessionNum, op, agentNum);
        } finally {
            if (sessionLock.isHeldByCurrentThread()) {
                sessionLock.unlock();
            }
        }
    }

    private String doEnsureBound(String sandboxNum, String sessionNum, String op, String agentNum) {
        var existing = runtimeRepository.findBoundBySessionNum(sessionNum);
        if (existing.isPresent()) {
            SandboxRuntimeInstanceRecord cur = existing.get();
            if (sandboxNum.equals(cur.sandboxNum())
                    && sandboxContainerGateway.isAlive(cur.opensandboxInstanceId())) {
                touchActive(cur, op);
                Sandbox asset = requireOnlineAsset(sandboxNum);
                renewQuietly(cur.opensandboxInstanceId(), resolveAliveMinutes(asset));
                return cur.opensandboxInstanceId();
            }
            log.warn("[sandbox-pool] bound instance dead or sandbox mismatch, rebind sessionNum={} "
                            + "instanceId={} boundSandbox={} want={}",
                    sessionNum, cur.opensandboxInstanceId(), cur.sandboxNum(), sandboxNum);
            safeKill(cur.opensandboxInstanceId());
            runtimeRepository.softDelete(cur.num());
        }

        boolean isolate = sandboxContainerGateway.isolatesWorkspaceBySession();
        String subPath = null;
        String effectiveAgentNum = agentNum;
        if (isolate) {
            Sandbox asset = requireOnlineAsset(sandboxNum);
            if (StrUtil.isBlank(effectiveAgentNum)) {
                effectiveAgentNum = lookupAgentNum(sessionNum);
            }
            try {
                subPath = SessionWorkspaceMount.subPath(
                        asset.getWorkspaceNum(), effectiveAgentNum, sessionNum);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), ex.getMessage());
            }
        }

        RLock assetLock = redissonClient.getLock(POOL_LOCK_PREFIX + sandboxNum);
        boolean assetAcquired;
        try {
            assetAcquired = assetLock.tryLock(ASSET_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱池操作被中断");
        }
        if (!assetAcquired) {
            throw new BusinessException(BizCode.CONFLICT.getCode(),
                    "沙箱池繁忙，请稍后重试");
        }
        try {
            Sandbox asset = requireOnlineAsset(sandboxNum);
            long alive = runtimeRepository.countAlive(sandboxNum);
            int maxConcurrent = asset.getMaxConcurrent() != null ? asset.getMaxConcurrent() : 8;
            if (alive >= maxConcurrent) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "沙箱并发实例已达上限（" + maxConcurrent + "），请稍后重试或扩大 maxConcurrent");
            }
            String osId;
            if (isolate) {
                osId = sandboxContainerGateway.create(
                        asset.getCpu(), asset.getMemoryMb(), asset.getAliveMinutes(), subPath);
                log.info("[sandbox-pool] session workspace sandboxNum={} sessionNum={} subPath={} instanceId={}",
                        sandboxNum, sessionNum, subPath, osId);
            } else {
                osId = sandboxContainerGateway.create(
                        asset.getCpu(), asset.getMemoryMb(), asset.getAliveMinutes());
                log.info("[sandbox-pool] create+bind sandboxNum={} sessionNum={} instanceId={}",
                        sandboxNum, sessionNum, osId);
            }
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
            renewQuietly(osId, resolveAliveMinutes(asset));
            return osId;
        } finally {
            if (assetLock.isHeldByCurrentThread()) {
                assetLock.unlock();
            }
        }
    }

    private static String awaitInflight(CompletableFuture<String> existing, String sessionNum) {
        try {
            return existing.get(INFLIGHT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException(BizCode.CONFLICT.getCode(),
                    "沙箱正在为该会话准备中，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱会话绑定被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(BizCode.SYSTEM_BUSY.getCode(),
                    "沙箱绑定失败 sessionNum=" + sessionNum + ": " + cause.getMessage());
        }
    }

    private void renewQuietly(String instanceId, int aliveMinutes) {
        if (StrUtil.isBlank(instanceId)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastRenewEpochMs.get(instanceId);
        if (prev != null && now - prev < RENEW_MIN_INTERVAL_MS) {
            return;
        }
        try {
            sandboxContainerGateway.renew(instanceId, aliveMinutes);
            lastRenewEpochMs.put(instanceId, now);
        } catch (Exception e) {
            log.warn("[sandbox-pool] renew failed instanceId={}: {}", instanceId, e.getMessage());
        }
    }

    private static int resolveAliveMinutes(Sandbox asset) {
        if (asset != null && asset.getAliveMinutes() != null && asset.getAliveMinutes() > 0) {
            return asset.getAliveMinutes();
        }
        return 30;
    }

    private void releaseOrDestroy(SandboxRuntimeInstanceRecord r, String operatorId, boolean preferIdle) {
        // 热池已下线：会话结束一律销毁，不回 IDLE
        safeKill(r.opensandboxInstanceId());
        runtimeRepository.softDelete(r.num());
        lastRenewEpochMs.remove(r.opensandboxInstanceId());
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
        lastRenewEpochMs.remove(instanceId);
    }
}
