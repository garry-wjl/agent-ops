package ink.garry.rd.agent.ws.adapter.agent.scheduler;

import ink.garry.rd.agent.ws.application.agent.A2aSyncApplicationService;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * A2A 同步定时任务（adapter 层入站调度入口）。
 * <p>
 * 与 {@code A2aSyncController}（HTTP 入口）对等：
 * 一个是定时触发（每 5 分钟），一个是运维手动触发。两者都打到 application 层
 * {@link A2aSyncApplicationService#syncPendingBatch}。
 * <p>
 * <b>并发控制</b>：多副本部署下经 Redisson 全局锁保证同一时刻只有一个实例执行本轮对账，
 * 避免重复 fetch Nacos、重复写状态。仿照 {@code SandboxReconcileScheduler} 模式：
 * 抢不到锁的副本直接跳过本轮，不阻塞。
 * <p>
 * <b>失败隔离</b>：整轮异常打 ERROR 后由下一周期重试；
 * 单条失败由 {@link A2aSyncApplicationService#syncPendingBatch} 内部已 try/catch 隔离。
 *
 * @see ink.garry.rd.agent.ws.adapter.agent.A2aSyncController
 * @see A2aSyncApplicationService
 */
@Slf4j
@Component
public class A2aSyncScheduler {

    /** 单次对账租约时长（秒）：280s 略小于 cron 间隔 300s（5min），超时由 Redisson 自动释放避免死锁。 */
    private static final long SYNC_LOCK_LEASE_SECONDS = 280L;

    @Resource
    private A2aSyncApplicationService a2aSyncApplicationService;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 每 5 分钟对账一次 PENDING_SYNC Agent 与 Nacos 真实状态。
     * <p>
     * 流程：抢全局对账锁（抢不到即跳过）→ 调 {@link A2aSyncApplicationService#syncPendingBatch}
     * 批量推进 → 释放锁。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void syncPending() {
        RLock lock = redissonClient.getLock(LockKeyConstant.A2A_SYNC_RECONCILE_LOCK);
        boolean acquired;
        try {
            // 不等待：抢不到说明其它副本正在跑，本副本直接跳过本轮
            acquired = lock.tryLock(0, SYNC_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[a2a-sync] interrupted while acquiring lock, skip this round");
            return;
        }
        if (!acquired) {
            log.debug("[a2a-sync] lock busy, another instance running, skip this round");
            return;
        }
        try {
            int processed = a2aSyncApplicationService.syncPendingBatch(
                    A2aSyncApplicationService.DEFAULT_BATCH_SIZE);
            log.info("[a2a-sync] round done, processed={}", processed);
        } catch (Exception ex) {
            log.error("[a2a-sync] round failed", ex);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}