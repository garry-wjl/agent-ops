package ink.garry.rd.agent.ws.adapter.sandbox.scheduler;

import cn.hutool.core.collection.CollUtil;
import ink.garry.rd.agent.ws.application.sandbox.SandboxQueryService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDTO;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 沙箱脏态对账定时任务（adapter 层入站调度入口）。
 * <p>
 * 周期：取在线资产 → {@link SandboxRunner#reconcile} 清理死亡 runtime →
 * {@link SandboxPoolService#reclaimInactiveSessions} 回收空闲超时 BOUND →
 * 开池资产 {@link SandboxPoolService#replenish} 补水位。
 * 会话隔离模型下资产 ONLINE 表示规格就绪，不再因资产表 instanceId 为空/死亡而下线资产。
 * 本类只负责调度 + 分布式锁。
 *
 * <h3>并发控制</h3>
 * 多副本部署下经 Redisson 全局锁 {@link LockKeyConstant#SANDBOX_RECONCILE_LOCK} 保证同一时刻
 * 只有一个实例执行，避免并发重复 kill / 校正；抢不到锁的副本直接跳过本轮。
 *
 * <h3>失败隔离</h3>
 * 单个沙箱对账异常仅打 WARN 不中断整轮；整轮异常打 ERROR 后由下一周期重试。
 */
@Slf4j
@Component
public class SandboxReconcileScheduler {

    /** 对账系统操作账号（与 application 层 SYSTEM_OPERATOR 约定一致）。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** 对账锁租约时长（秒）：覆盖单轮对账，超时由 Redisson 自动释放避免死锁。 */
    private static final long RECONCILE_LOCK_LEASE_SECONDS = 280L;

    @Resource
    private SandboxQueryService sandboxQueryService;
    @Resource
    private SandboxRunner sandboxRunner;
    @Resource
    private SandboxPoolService sandboxPoolService;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 每 5 分钟对账一次在线沙箱与 OpenSandbox 真实状态。
     * <p>
     * 流程：抢全局对账锁（抢不到即跳过）→ 取在线清单 → 逐一判活校正 → 释放锁。
     * 幂等：逐沙箱按真实状态校正，重复执行结果一致。
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void reconcile() {
        RLock lock = redissonClient.getLock(LockKeyConstant.SANDBOX_RECONCILE_LOCK);
        boolean acquired;
        try {
            // 不等待：抢不到说明其它副本正在跑，本副本直接跳过本轮
            acquired = lock.tryLock(0, RECONCILE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[sandbox-reconcile] interrupted while acquiring lock, skip this round");
            return;
        }
        if (!acquired) {
            log.debug("[sandbox-reconcile] lock busy, another instance running, skip this round");
            return;
        }
        try {
            doReconcile();
        } catch (Exception ex) {
            log.error("[sandbox-reconcile] round failed", ex);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 取在线清单并逐一判活校正；单沙箱异常不中断整轮。 */
    private void doReconcile() {
        List<SandboxDTO> onlineList = sandboxQueryService.listOnlineSandboxes();
        if (CollUtil.isEmpty(onlineList)) {
            log.debug("[sandbox-reconcile] no online sandbox, nothing to do");
            return;
        }
        int total = onlineList.size();
        int corrected = 0;
        for (SandboxDTO dto : onlineList) {
            try {
                String before = dto.getStatus();
                sandboxRunner.reconcile(dto.getNum(), dto.getSandboxInstanceId(), SYSTEM_OPERATOR);
                // reconcile 内部仅在不存活时回写下线；此处无法直接得知是否校正，仅统计调用数据由日志兜底
                corrected++;
                log.debug("[sandbox-reconcile] checked num={}, beforeStatus={}", dto.getNum(), before);
            } catch (Exception ex) {
                log.warn("[sandbox-reconcile] sandbox reconcile failed, num={}, instanceId={}, reason={}",
                        dto.getNum(), dto.getSandboxInstanceId(), ex.getMessage());
            }
        }
        log.info("[sandbox-reconcile] round done, online={}, checked={}", total, corrected);
        try {
            sandboxPoolService.reclaimInactiveSessions();
        } catch (Exception e) {
            log.warn("[sandbox-reconcile] reclaim failed: {}", e.getMessage());
        }
        for (SandboxDTO dto : onlineList) {
            try {
                if (Boolean.TRUE.equals(dto.getPoolEnabled())) {
                    sandboxPoolService.replenish(dto.getNum(), SYSTEM_OPERATOR);
                }
            } catch (Exception e) {
                log.warn("[sandbox-reconcile] replenish failed num={}: {}", dto.getNum(), e.getMessage());
            }
        }
    }
}
