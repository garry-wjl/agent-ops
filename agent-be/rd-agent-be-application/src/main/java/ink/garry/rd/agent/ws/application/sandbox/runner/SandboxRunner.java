package ink.garry.rd.agent.ws.application.sandbox.runner;

import cn.hutool.core.util.StrUtil;
import com.alibaba.opensandbox.sandbox.Sandbox;
import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.sandbox.dto.SandboxDomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxClient;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.common.constant.RedisKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 沙箱运行时编排服务（容器编排的<b>唯一落点</b>）。
 * <p>
 * 直接注入 infra 工具类 {@link SandboxClient} 完成容器的创建 / 健康检查 / 销毁 / 判活
 * （{@code application → infra} 为本项目允许方向，参 {@code SandboxService} 用 {@code SandboxClient} 的先例），
 * <b>不</b>经任何领域网关；容器动作完成后回调 {@link SandboxCommandService} 的状态流转方法落库
 * （事务 + 锁统一在 CommandService 内），本服务自身不开事务、不持锁。
 *
 * <h3>分层：监听入口在 adapter，编排逻辑在本服务</h3>
 * 领域事件的 {@code @EventListener} 入口位于 adapter 层
 * {@code adapter.sandbox.listener.SandboxDomainEventListener}（与「listener 属 adapter」的架构约定一致），
 * 该监听器只接收 facade 的 {@link DomainEventDTO} 信封、按线程池异步分派，再委派到本服务的
 * {@link #provision(DomainEventDTO)} / {@link #destroyContainer(DomainEventDTO)}；
 * <b>领域事件载荷（{@link SandboxDomainEventDTO}，domain 业务类型）的解析与容器编排全部在本应用层完成</b>，
 * adapter 不接触任何 domain 业务类型。
 *
 * <h3>命名避让</h3>
 * 命名为 {@code SandboxRunner}，与既有 Agent 运行时 {@code SandboxService}（会话→容器映射）区分，互不影响。
 *
 * <h3>编排职责（沙箱管理技术方案 §6.2.3）</h3>
 * <ul>
 *   <li>{@link #provision(DomainEventDTO)}（监听 {@code SANDBOX_SUBMITTED}）：按规格建容器 + 健康检查
 *       → 成功调 {@link SandboxCommandService#onlineSandbox}、失败调
 *       {@link SandboxCommandService#markProvisionFailed}；</li>
 *   <li>{@link #destroyContainer(DomainEventDTO)}（监听 {@code SANDBOX_OFFLINED} / {@code SANDBOX_DELETED}）：
 *       取载荷 instanceId 后 kill 容器（kill 失败仅 WARN，容器 TTL 兜底回收）；</li>
 *   <li>{@link #reconcile(String, String, String)}（供对账 Scheduler 调用）：判活，不存活则回写下线。</li>
 * </ul>
 *
 * <h3>幂等</h3>
 * 回写方法（onlineSandbox / markProvisionFailed）经聚合状态前置校验（仅 INITIALIZED 可流转），
 * 事件重复投递时非初始化态由聚合断言拦截，本服务对其 BusinessException 仅记日志、不再重试。
 */
@Slf4j
@Service
public class SandboxRunner {

    /** 本服务关注的供给事件类型（提交 / 重新上线均发此事件）。 */
    public static final String PROVISION_EVENT_TYPE = DomainEventConstant.SANDBOX_SUBMITTED;

    /** 本服务关注的销毁容器事件类型集合（下线 / 删除）。 */
    public static final Set<String> DESTROY_EVENT_TYPES = Set.of(
            DomainEventConstant.SANDBOX_OFFLINED,
            DomainEventConstant.SANDBOX_DELETED);

    /** 锁等待时长(秒)：抢不到锁最多再等 3s 放弃。 */
    private static final long LOCK_WAIT_SECONDS = 3L;

    /** 锁租约时长(秒)：超时由 Redisson 自动释放,避免死锁。 */
    private static final long LOCK_LEASE_SECONDS = 30L;

    /** session 映射 value 分隔符:{@code sandboxId::execdSessionId}。 */
    private static final String SESSION_VALUE_SEP = "::";

    @Resource
    private SandboxClient sandboxClient;
    @Resource
    private SandboxCommandService sandboxCommandService;

    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // 供给容器（由 adapter 监听 SANDBOX_SUBMITTED 后委派）
    // ============================================================

    /**
     * 异步供给容器：建容器 + 健康检查 → 上线 / 标记失败。
     * <p>
     * 仅处理 {@code SANDBOX_SUBMITTED} 类型（adapter 监听器已挂线程池异步分派）；其余事件直接忽略。
     * 由 adapter 层 {@code SandboxDomainEventListener} 委派调用。
     *
     * @param event 领域事件信封（载荷为 {@link SandboxDomainEventDTO}）
     */
    public void provision(DomainEventDTO event) {
        if (event == null || !PROVISION_EVENT_TYPE.equals(event.getType())) {
            return;
        }
        SandboxDomainEventDTO payload = asPayload(event);
        if (payload == null) {
            return;
        }
        String num = payload.getNum();
        String operatorId = payload.getOperatorEmpNo();
        log.info("[sandbox-provision] start num={}, cpu={}, memoryMb={}, aliveMinutes={}",
                num, payload.getCpu(), payload.getMemoryMb(), payload.getAliveMinutes());

        String instanceId = null;
        try {
            // 1. 按规格建容器（create 内已 skipHealthCheck=false，等待 Running + execd ping）
            instanceId = sandboxClient.create(payload.getCpu(), payload.getMemoryMb(), payload.getAliveMinutes());
            // 2. 再次按 OpenSandbox 就绪语义探活（不 skip；与运行时 connect 区分）
            probeReady(instanceId);
            // 3. 供给成功：回写上线
            sandboxCommandService.onlineSandbox(num, instanceId, operatorId);
            log.info("[sandbox-provision] online num={}, instanceId={}", num, instanceId);
        } catch (Exception ex) {
            log.error("[sandbox-provision] failed num={}, instanceId={}, reason={}",
                    num, instanceId, ex.getMessage(), ex);
            // 已建容器则尝试 kill 回收，避免泄漏（失败仅 WARN，TTL 兜底）
            safeKill(instanceId);
            // 回写失败态（携带原因）；非初始化态由聚合拦截，这里再吞一次异常仅记日志
            try {
                sandboxCommandService.markProvisionFailed(num, truncateReason(ex.getMessage()), operatorId);
            } catch (Exception inner) {
                log.warn("[sandbox-provision] markProvisionFailed also failed num={}, reason={}",
                        num, inner.getMessage());
            }
        }
    }

    // ============================================================
    // 销毁容器（由 adapter 监听 SANDBOX_OFFLINED / SANDBOX_DELETED 后委派）
    // ============================================================

    /**
     * 销毁底层容器。
     * <p>
     * 取载荷 sandboxInstanceId，非空则 kill；kill 失败仅打 WARN（容器 TTL 兜底回收），不阻断主流程。
     * 仅处理 {@code SANDBOX_OFFLINED} / {@code SANDBOX_DELETED}。由 adapter 层
     * {@code SandboxDomainEventListener} 委派调用。
     *
     * @param event 领域事件信封（载荷为 {@link SandboxDomainEventDTO}）
     */
    public void destroyContainer(DomainEventDTO event) {
        if (event == null || !DESTROY_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        SandboxDomainEventDTO payload = asPayload(event);
        if (payload == null) {
            return;
        }
        String instanceId = payload.getSandboxInstanceId();
        if (StrUtil.isBlank(instanceId)) {
            log.info("[sandbox-destroy] no instanceId, skip kill. type={}, num={}",
                    event.getType(), payload.getNum());
            return;
        }
        log.info("[sandbox-destroy] kill container type={}, num={}, instanceId={}",
                event.getType(), payload.getNum(), instanceId);
        safeKill(instanceId);
    }

    // ============================================================
    // 对账判活（供 SandboxReconcileScheduler 调用）
    // ============================================================

    /**
     * 脏态对账：判定在线沙箱的底层容器是否仍存活，不存活则回写下线。
     * <p>
     * 由 {@code adapter.sandbox.scheduler.SandboxReconcileScheduler} 在持有全局对账锁后逐一调用；
     * 判活经 {@link SandboxClient#isAlive(String)}，判定不存活后回调
     * {@link SandboxCommandService#reconcileToOffline}（在线→下线）。单沙箱异常不上抛，由调度方继续下一个。
     *
     * @param num        沙箱业务编号
     * @param instanceId 当前容器实例 id（为空视为不存活）
     * @param operatorId 对账系统操作账号
     */
    public void reconcile(String num, String instanceId, String operatorId) {
        if (StrUtil.isBlank(num)) {
            return;
        }
        boolean alive = StrUtil.isNotBlank(instanceId) && sandboxClient.isAlive(instanceId);
        if (alive) {
            return;
        }
        log.warn("[sandbox-reconcile] container not alive, correct to OFFLINE. num={}, instanceId={}",
                num, instanceId);
        sandboxCommandService.reconcileToOffline(num, operatorId);
    }

    /**
     * 获取会话级 bash session。
     * <p>
     * 校验映射中的 sandboxId 是否与当前容器一致:不一致(容器已重建)则丢弃旧 session 重新创建。
     *
     * @param sandboxId  容器 id
     * @param sessionNum 会话编号
     * @param env        会话环境变量
     * @param ttlMinutes session 存活时长（分钟）
     * @return session
     */
    public SandboxSession obtainSession(String sandboxId, String sessionNum, Map<String, String> env, long ttlMinutes) {
        String execdSessionId = obtainExecdSession(sandboxId, sessionNum, env, ttlMinutes);
        Sandbox sandbox = sandboxClient.connect(sandboxId);
        return new SandboxSession(sandbox, execdSessionId);
    }

    /**
     * 在指定容器内解析 / 创建会话级 bash session,返回 execdSessionId,并滑动续期映射。
     * <p>
     * 校验映射中的 sandboxId 是否与当前容器一致:不一致(容器已重建)则丢弃旧 session 重新创建。
     */
    private String obtainExecdSession(String sandboxId, String sessionNum, Map<String, String> env, long ttlMinutes) {
        RBucket<String> bucket = sessionBucket(sessionNum);
        String mapped = bucket.get();
        if (mapped != null) {
            String[] parts = mapped.split(SESSION_VALUE_SEP, 2);
            if (parts.length == 2 && parts[0].equals(sandboxId)) {
                bucket.expire(Duration.ofMinutes(ttlMinutes));
                return parts[1];
            }
            log.info("execd session stale (container changed), recreating. sessionNum={}, old={}, sandboxId={}",
                    sessionNum, mapped, sandboxId);
        }
        return runWithLock(LockKeyConstant.SANDBOX_SESSION_LOCK_PREFIX + sessionNum, () -> {
            String existing = bucket.get();
            if (existing != null) {
                String[] parts = existing.split(SESSION_VALUE_SEP, 2);
                if (parts.length == 2 && parts[0].equals(sandboxId)) {
                    bucket.expire(Duration.ofMinutes(ttlMinutes));
                    return parts[1];
                }
            }
            try (Sandbox sandbox = sandboxClient.connect(sandboxId)) {
                String execdSessionId = sandbox.commands().createSession(null);
                bucket.set(sandboxId + SESSION_VALUE_SEP + execdSessionId, Duration.ofMinutes(ttlMinutes));
                log.info("session bound to new execd session, sessionNum={}, sandboxId={}, execdSessionId={}",
                        sessionNum, sandboxId, execdSessionId);
                return execdSessionId;
            }
        });
    }

    private RBucket<String> sessionBucket(String sessionNum) {
        return redissonClient.getBucket(RedisKeyConstant.SANDBOX_SESSION_PREFIX + sessionNum);
    }

    /**
     * 健康检查：按 OpenSandbox 就绪语义（Running + execd ping）断言容器可用。
     * <p>
     * 委托 {@link SandboxClient#assertReady(String)}；失败抛异常由调用方转入供给失败分支。
     * 勿使用 {@link SandboxClient#connect(String)}——其 skipHealthCheck=true，不能证明 execd 可用。
     */
    private void probeReady(String instanceId) {
        sandboxClient.assertReady(instanceId);
        log.debug("[sandbox-provision] probe ready ok, instanceId={}", instanceId);
    }

    /** kill 容器，失败仅 WARN（TTL 兜底回收），不上抛。 */
    private void safeKill(String instanceId) {
        if (StrUtil.isBlank(instanceId)) {
            return;
        }
        try {
            sandboxClient.kill(instanceId);
        } catch (Exception e) {
            log.warn("[sandbox] kill container failed, rely on TTL recycle. instanceId={}, reason={}",
                    instanceId, e.getMessage());
        }
    }

    /** 取事件载荷并做类型校验；非 SandboxDomainEventDTO 记 WARN 后返回 null。 */
    private SandboxDomainEventDTO asPayload(DomainEventDTO event) {
        Object data = event.getData();
        if (data instanceof SandboxDomainEventDTO payload) {
            return payload;
        }
        log.warn("[sandbox-event] unexpected payload type={}, eventType={}",
                data == null ? "null" : data.getClass().getName(), event.getType());
        return null;
    }

    /** 失败原因截断到 ≤200 字，避免超长写入事件 / 日志。 */
    private static String truncateReason(String reason) {
        if (reason == null) {
            return "容器供给失败（原因未知）";
        }
        return reason.length() <= 200 ? reason : reason.substring(0, 200);
    }

    /**
     * 以给定 key 抢分布式锁后执行临界区(与 {@code SkillCommandService#runWithLock} 同款写法)。
     *
     * @param key    完整锁 key(已拼前缀 + 业务 id)
     * @param action 临界区操作
     * @return action 返回值
     * @throws BusinessException 抢锁失败或线程中断({@link BizCode#CONFLICT})
     */
    private <T> T runWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱获取被中断");
        }
        if (!acquired) {
            log.warn("sandbox lock busy key={}", key);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱正在初始化中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
