package ink.garry.rd.agent.ws.application.sandbox;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.constant.SandboxConstants;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxCreateParamDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.factory.SandboxFactory;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Sandbox 写侧应用服务。
 * <p>
 * 参照 {@code WorkspaceCommandService} / {@code SkillCommandService}：注入 domain
 * {@link SandboxFactory}（拿聚合 / 调领域动作）+ 读侧 {@link SandboxQueryService}
 * （CQRS：唯一性预检走 QueryService，不直接调 Mapper）+ {@link RedissonClient}（用例级互斥锁）。
 * 所有写方法均标 {@code @Transactional(rollbackFor = Exception.class)}，事务范围内嵌在分布式锁区域内。
 *
 * <h3>方法集（沙箱管理技术方案 §6.2.1）</h3>
 * <ul>
 *   <li>用户触发：createSandbox / updateSandbox / deleteSandbox / submitSandbox /
 *       offlineSandbox / reonlineSandbox；</li>
 *   <li>{@code SandboxRunner} 回写：onlineSandbox / markProvisionFailed / reconcileToOffline。</li>
 * </ul>
 *
 * <h3>分层约束</h3>
 * 聚合 {@link Sandbox} 是纯状态机，只碰仓储 / 编号网关 / 事件，<b>不</b>调 OpenSandbox；
 * 容器编排集中在 {@link SandboxRunner}。本服务禁止注入 / 调用 domain Repository 或 Gateway，
 * 加载聚合统一经 {@link SandboxFactory}。operatorId 由 adapter（用户操作）或
 * {@link SandboxRunner}（异步回写 / 对账）传入。
 */
@Slf4j
@Service
public class SandboxCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s（与 {@code WorkspaceCommandService} 统一）。 */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布的整条用例，超时由 Redisson 自动释放。 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private SandboxFactory sandboxFactory;
    @Resource
    private SandboxQueryService sandboxQueryService;
    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // createSandbox
    // ============================================================

    /**
     * 新建沙箱（草稿态落库）。
     *
     * @param param      创建入参（workspaceNum / name / type / cpu / memoryMb / aliveMinutes / remark）
     * @param operatorId 操作人工号
     * @return 新沙箱 DTO（含生成的 num，status=DRAFT）
     * @throws BusinessException 参数非法 / 同空间名称冲突 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public SandboxDTO createSandbox(SandboxCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getWorkspaceNum(), "归属工作空间编号不能为空");
        validateName(param.getName());
        validateSpec(param.getCpu(), param.getMemoryMb(), param.getAliveMinutes());
        validateRemark(param.getRemark());
        SandboxType type = resolveType(param.getType());

        // 按 (workspaceNum, name) 抢创建锁：num 尚未生成，防连点 / 重试创建同空间同名沙箱
        String lockKey = LockKeyConstant.SANDBOX_CREATE_LOCK_PREFIX
                + param.getWorkspaceNum() + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            // 名称唯一性预检（CQRS：走 QueryService）
            if (sandboxQueryService.existsByWorkspaceAndName(param.getWorkspaceNum(), param.getName())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同一空间内已存在同名沙箱，请更换");
            }
            Sandbox sandbox = sandboxFactory.buildSandbox(
                    param.getWorkspaceNum(), param.getName(), type,
                    param.getCpu(), param.getMemoryMb(), param.getAliveMinutes(), param.getRemark());
            // 领域动作：聚合内统一校验全部不变量并落库 + 发 SANDBOX_CREATED
            sandbox.save(operatorId);
            return toDTO(sandbox);
        });
    }

    // ============================================================
    // updateSandbox
    // ============================================================

    /**
     * 编辑沙箱（按当前状态约束可改字段）。
     * <p>
     * 草稿 / 失败态可改规格（name / cpu / memoryMb / aliveMinutes / remark）；其余态仅可改 remark
     * （沙箱管理技术方案 §4.2.3）。规格字段的写入与否由本应用层按 status 决定，领域不设独立动作。
     *
     * @param param      编辑入参（num + 待改字段）
     * @param operatorId 操作人工号
     * @throws BusinessException 沙箱不存在 / 名称冲突 / 参数非法 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSandbox(SandboxUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getNum(), "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateRemark(param.getRemark());

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(param.getNum());
            boolean specEditable = sandbox.getStatus() == SandboxStatus.DRAFT
                    || sandbox.getStatus() == SandboxStatus.FAILED;
            if (specEditable) {
                // 草稿 / 失败态：可改全部规格字段
                validateName(param.getName());
                validateSpec(param.getCpu(), param.getMemoryMb(), param.getAliveMinutes());
                // 名称有变化时做同空间唯一性预检
                if (!StrUtil.equals(sandbox.getName(), param.getName())
                        && sandboxQueryService.existsByWorkspaceAndName(sandbox.getWorkspaceNum(), param.getName())) {
                    throw new BusinessException(BizCode.CONFLICT.getCode(), "同一空间内已存在同名沙箱，请更换");
                }
                sandbox.setName(param.getName());
                sandbox.setCpu(param.getCpu());
                sandbox.setMemoryMb(param.getMemoryMb());
                sandbox.setAliveMinutes(param.getAliveMinutes());
            }
            // 备注任意非删除态可改
            sandbox.setRemark(param.getRemark());
            // 整聚合覆盖落库（聚合内统一校验不变量并发 SANDBOX_UPDATED）
            sandbox.save(operatorId);
            return null;
        });
    }

    // ============================================================
    // deleteSandbox
    // ============================================================

    /**
     * 软删沙箱（在线态禁删，由聚合校验）。
     * <p>底层容器的 kill 由 {@link SandboxRunner} 监听 {@code SANDBOX_DELETED} 事件执行。
     *
     * @param num        沙箱业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 沙箱不存在 / 在线态禁删 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSandbox(String num, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            // 领域动作：软删（deleted=1）+ 发 SANDBOX_DELETED（在线态禁删由聚合内校验）
            sandbox.delete(operatorId);
            return null;
        });
    }

    // ============================================================
    // submitSandbox
    // ============================================================

    /**
     * 提交沙箱：草稿 / 失败 → 初始化（同步快路径，不调 OpenSandbox）。
     * <p>聚合置初始化态 + 发 {@code SANDBOX_SUBMITTED}，{@link SandboxRunner} 监听后异步建容器。
     *
     * @param num        沙箱业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 沙箱不存在 / 非草稿或失败态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitSandbox(String num, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.submit(operatorId);
            return null;
        });
    }

    // ============================================================
    // offlineSandbox
    // ============================================================

    /**
     * 下线沙箱：在线 → 下线。
     * <p>容器的 kill 由 {@link SandboxRunner} 监听 {@code SANDBOX_OFFLINED} 事件执行。
     *
     * @param num        沙箱业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 沙箱不存在 / 非在线态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void offlineSandbox(String num, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.offline(operatorId);
            return null;
        });
    }

    // ============================================================
    // reonlineSandbox
    // ============================================================

    /**
     * 重新上线沙箱：下线 → 初始化（重走供给流程）。
     * <p>语义同提交：聚合置初始化态 + 发 {@code SANDBOX_SUBMITTED}，{@link SandboxRunner} 监听后异步建容器。
     *
     * @param num        沙箱业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 沙箱不存在 / 非下线态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void reonlineSandbox(String num, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.reonline(operatorId);
            return null;
        });
    }

    // ============================================================
    // onlineSandbox（SandboxRunner 容器就绪后回写）
    // ============================================================

    /**
     * 上线回写：初始化 → 在线（由 {@link SandboxRunner} 在容器创建 + 健康检查通过后回调）。
     * <p>
     * 幂等：聚合内校验仅初始化态可上线，事件 / 回调重复投递时非初始化态由聚合断言拦截。
     *
     * @param num        沙箱业务编号
     * @param instanceId 已就绪的 OpenSandbox 容器实例 id
     * @param operatorId 操作人工号（来自原供给事件载荷）
     * @throws BusinessException 沙箱不存在 / 非初始化态 / 实例 id 为空 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void onlineSandbox(String num, String instanceId, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(instanceId, "容器实例 id 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.online(instanceId, operatorId);
            return null;
        });
    }

    // ============================================================
    // markProvisionFailed（SandboxRunner 供给失败回写）
    // ============================================================

    /**
     * 标记供给失败：初始化 → 失败（由 {@link SandboxRunner} 在容器创建 / 健康检查失败时回调）。
     *
     * @param num        沙箱业务编号
     * @param reason     失败原因（随 {@code SANDBOX_PROVISION_FAILED} 事件载荷传出）
     * @param operatorId 操作人工号（来自原供给事件载荷）
     * @throws BusinessException 沙箱不存在 / 非初始化态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void markProvisionFailed(String num, String reason, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.markProvisionFailed(reason, operatorId);
            return null;
        });
    }

    // ============================================================
    // reconcileToOffline（对账校正回写）
    // ============================================================

    /**
     * 脏态对账校正：在线 → 下线（由 {@link SandboxRunner} 判活为不存活后回调）。
     *
     * @param num        沙箱业务编号
     * @param operatorId 操作人工号（对账系统账号）
     * @throws BusinessException 沙箱不存在 / 非在线态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void reconcileToOffline(String num, String operatorId) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Sandbox sandbox = requireByNum(num);
            sandbox.reconcileToOffline(operatorId);
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 经工厂按 num 加载聚合；不存在抛 {@link BizCode#NOT_FOUND}。 */
    private Sandbox requireByNum(String num) {
        Sandbox sandbox = sandboxFactory.buildSandboxByNum(num);
        if (sandbox == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在 num=" + num);
        }
        return sandbox;
    }

    /** 解析沙箱类型字符串：为空兜底 CODE，非法值抛 {@link BizCode#INVALID_PARAM}。 */
    private static SandboxType resolveType(String type) {
        if (StrUtil.isBlank(type)) {
            return SandboxType.CODE;
        }
        try {
            return SandboxType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "不支持的沙箱类型：" + type);
        }
    }

    /** 名称长度 [1, 64] 校验（聚合内亦会校验，此处提前给出明确错误码）。 */
    private static void validateName(String name) {
        Assert.notBlank(name, "沙箱名称不能为空");
        if (name.length() > SandboxConstants.NAME_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "沙箱名称必须在 1~64 字符之间");
        }
    }

    /** 备注长度 ≤100 校验。 */
    private static void validateRemark(String remark) {
        if (remark != null && remark.length() > SandboxConstants.REMARK_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "备注不超过 100 字");
        }
    }

    /**
     * 规格字段校验：CPU 0.5 步进且区间 [0.5,16]、内存 [128,65536]、存活 [1,1440]
     * （聚合内亦会校验，此处提前给出明确错误码）。
     */
    private static void validateSpec(BigDecimal cpu, Integer memoryMb, Integer aliveMinutes) {
        BigDecimal cpuMin = new BigDecimal(SandboxConstants.CPU_MIN);
        BigDecimal cpuMax = new BigDecimal(SandboxConstants.CPU_MAX);
        BigDecimal cpuStep = new BigDecimal(SandboxConstants.CPU_STEP);
        if (cpu == null || cpu.compareTo(cpuMin) < 0 || cpu.compareTo(cpuMax) > 0
                || cpu.remainder(cpuStep).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "CPU 需为 0.5 核的整数倍，区间 0.5~16");
        }
        if (memoryMb == null || memoryMb < SandboxConstants.MEMORY_MIN || memoryMb > SandboxConstants.MEMORY_MAX) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "内存需在 128~65536 MB 之间");
        }
        if (aliveMinutes == null || aliveMinutes < SandboxConstants.ALIVE_MIN || aliveMinutes > SandboxConstants.ALIVE_MAX) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "容器存活时间需在 1~1440 分钟之间");
        }
    }

    /** 领域对象 → SandboxDTO（命令返回用，纯字段映射）。 */
    private static SandboxDTO toDTO(Sandbox s) {
        return SandboxDTO.builder()
                .num(s.getNum())
                .workspaceNum(s.getWorkspaceNum())
                .name(s.getName())
                .type(s.getType() == null ? null : s.getType().name())
                .cpu(s.getCpu())
                .memoryMb(s.getMemoryMb())
                .aliveMinutes(s.getAliveMinutes())
                .status(s.getStatus() == null ? null : s.getStatus().name())
                .remark(s.getRemark())
                .sandboxInstanceId(s.getSandboxInstanceId())
                .createNo(s.getCreateNo())
                .updateNo(s.getUpdateNo())
                .createTime(s.getCreateTime())
                .updateTime(s.getUpdateTime())
                .build();
    }

    /**
     * 抢用例级分布式锁后执行编排；样板代码统一收口（参照 {@code WorkspaceCommandService}）。
     * 锁内再开事务（方法级 {@code @Transactional}），保证「先锁后事务」。
     *
     * @param lockKey 锁键（已含业务维度后缀）
     * @param action  临界区操作
     * @param <T>     返回类型
     * @return action 返回值
     * @throws BusinessException 抢锁失败（{@link BizCode#CONFLICT}）或线程中断
     */
    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱操作被中断");
        }
        if (!acquired) {
            log.warn("sandbox command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱正在处理中，请稍后重试");
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
