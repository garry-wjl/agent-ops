package ink.garry.rd.agent.ws.domain.sandbox;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.sandbox.dto.SandboxDomainEventDTO;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRepository;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sandbox 聚合根。
 * <p>
 * 基于阿里 OpenSandbox 的代码沙箱资产，承载五态生命周期（详见沙箱管理技术方案 §4.2）。
 * 状态机：
 * <pre>
 *   草稿 DRAFT ──submit──▶ 初始化 INITIALIZED ──online(供给成功)──▶ 在线 ONLINE
 *      ▲  ▲                     │                                    │
 *      │  │           markProvisionFailed(供给失败)             offline
 *      │  └──────────────────── ▼                                    ▼
 *   编辑(save)              失败 FAILED ──submit──▶ 初始化        下线 OFFLINE ──reonline──▶ 初始化
 * </pre>
 * <p>
 * <b>职责边界</b>：本聚合是<b>纯状态机</b> —— 只负责状态流转、不变量校验、持久化与发事件，
 * <b>不</b>直接调用 OpenSandbox。容器的创建 / 健康检查 / 销毁 / 存活查询是应用层职责，
 * 由 {@code application.sandbox.SandboxRunner} 监听领域事件后，直接使用 infra 的
 * {@code SandboxClient} 工具类完成；完成后再调用本聚合的 {@link #online(String, String)} /
 * {@link #markProvisionFailed(String, String)} 等动作回写状态。
 * <ul>
 *   <li>{@link #submit(String)}：草稿 / 失败 → 初始化，发 {@code SANDBOX_SUBMITTED}
 *       （{@code SandboxRunner} 监听后异步建容器）。</li>
 *   <li>{@link #online(String, String)}：初始化 → 在线，由 {@code SandboxRunner} 在容器就绪后
 *       带 sandboxInstanceId 调用。</li>
 *   <li>{@link #markProvisionFailed(String, String)}：初始化 → 失败，供给失败时由
 *       {@code SandboxRunner} 调用。</li>
 *   <li>{@link #offline(String)}：在线 → 下线，发 {@code SANDBOX_OFFLINED}
 *       （{@code SandboxRunner} 监听后 kill 容器）。</li>
 *   <li>{@link #reonline(String)}：下线 → 初始化（重走供给），发 {@code SANDBOX_SUBMITTED}。</li>
 *   <li>{@link #delete(String)}：软删；在线态禁删；发 {@code SANDBOX_DELETED}
 *       （{@code SandboxRunner} 监听后 kill 残留容器）。</li>
 * </ul>
 */
@Getter
@Setter
public class Sandbox extends DomainEntity {

    /** 名称长度上限（与 client.sandbox.constant.SandboxConstants 保持一致）。 */
    private static final int NAME_MAX_LENGTH = 64;
    /** 备注长度上限。 */
    private static final int REMARK_MAX_LENGTH = 100;
    /** CPU 下限（核）。 */
    private static final BigDecimal CPU_MIN = new BigDecimal("0.5");
    /** CPU 上限（核）。 */
    private static final BigDecimal CPU_MAX = new BigDecimal("16");
    /** CPU 步进单位（0.5 核）。 */
    private static final BigDecimal CPU_STEP = new BigDecimal("0.5");
    /** 内存下限（MB）。 */
    private static final int MEMORY_MIN = 128;
    /** 内存上限（MB）。 */
    private static final int MEMORY_MAX = 65536;
    /** 存活时间下限（分钟）。 */
    private static final int ALIVE_MIN = 1;
    /** 存活时间上限（分钟，24h）。 */
    private static final int ALIVE_MAX = 1440;

    // ---- 业务字段 ----

    /** 沙箱业务编号（前缀 SBX，由 {@link SandboxGateway#generateSandboxNum()} 生成）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 沙箱名称；同一工作空间内不重复（应用层经唯一性预检 + DB 唯一索引兜底）。 */
    private String name;

    /** 沙箱类型（本期固定 {@link SandboxType#CODE}）。 */
    private SandboxType type;

    /** CPU 核数（0.5 步进，区间 [0.5, 16]）。 */
    private BigDecimal cpu;

    /** 内存大小（MB，区间 [128, 65536]）。 */
    private Integer memoryMb;

    /** 容器存活时间（分钟，区间 [1, 1440]）；作为 OpenSandbox 容器 timeout 上限。 */
    private Integer aliveMinutes;

    /** 生命周期状态；详见 {@link SandboxStatus}。 */
    private SandboxStatus status;

    /** 备注（可空，≤100 字）。 */
    private String remark;

    /** OpenSandbox 容器实例 id；草稿 / 失败态为空，供给成功后由应用层回写。 */
    private String sandboxInstanceId;

    // ---- 装配依赖（由 SandboxFactory 在创建时装配） ----

    /** 装配依赖：Sandbox 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient SandboxRepository sandboxRepository;
    /** 装配依赖：Sandbox 业务编号生成网关。 */
    private transient SandboxGateway sandboxGateway;
    /** 装配依赖：领域事件发布器。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（infra RepositoryImpl 按 num 重建聚合时用无参 + setter 装配）。 */
    public Sandbox() {
    }

    /**
     * 必填字段 + 装配依赖构造方法（由 {@code SandboxFactory.buildSandbox} 创建新聚合时调用）。
     * <p>
     * 仅接收构建聚合所必须的数据字段与三个装配依赖；不接收由状态机控制的 status、
     * 系统生成的 num / sandboxInstanceId 以及审计字段，这些在 {@link #save(String)} 与
     * 各领域动作中统一处理。
     *
     * @param workspaceNum         归属工作空间业务编号
     * @param name                 沙箱名称
     * @param type                 沙箱类型
     * @param cpu                  CPU 核数
     * @param memoryMb             内存大小（MB）
     * @param aliveMinutes         容器存活时间（分钟）
     * @param remark               备注（可空）
     * @param sandboxRepository    Sandbox 仓储
     * @param sandboxGateway       Sandbox 业务编号生成网关
     * @param domainEventPublisher 领域事件发布器
     */
    public Sandbox(String workspaceNum,
                   String name,
                   SandboxType type,
                   BigDecimal cpu,
                   Integer memoryMb,
                   Integer aliveMinutes,
                   String remark,
                   SandboxRepository sandboxRepository,
                   SandboxGateway sandboxGateway,
                   DomainEventPublisher domainEventPublisher) {
        this.workspaceNum = workspaceNum;
        this.name = name;
        this.type = type;
        this.cpu = cpu;
        this.memoryMb = memoryMb;
        this.aliveMinutes = aliveMinutes;
        this.remark = remark;
        this.sandboxRepository = sandboxRepository;
        this.sandboxGateway = sandboxGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：名称 / 类型 / 状态非空且合法；CPU 0.5 步进且在区间内；
     * 内存、存活时间在区间内；备注长度 ≤100。
     */
    @Override
    public void domainValidate() {
        // 名称 [1, 64]
        Assert.notBlank(name, "沙箱名称必须在 1~64 字符之间");
        Assert.isTrue(name.length() <= NAME_MAX_LENGTH, "沙箱名称必须在 1~64 字符之间");
        // 工作空间归属
        Assert.notBlank(workspaceNum, "归属工作空间编号不能为空");
        // 类型
        Assert.notNull(type, "沙箱类型不能为空");
        // CPU：非空、≥0.5、≤16、且为 0.5 的整数倍
        Assert.notNull(cpu, "CPU 核数不能为空");
        Assert.isTrue(cpu.compareTo(CPU_MIN) >= 0 && cpu.compareTo(CPU_MAX) <= 0,
                "CPU 需在 0.5~16 核之间");
        Assert.isTrue(cpu.remainder(CPU_STEP).compareTo(BigDecimal.ZERO) == 0,
                "CPU 需为 0.5 核的整数倍");
        // 内存 [128, 65536]
        Assert.notNull(memoryMb, "内存大小不能为空");
        Assert.isTrue(memoryMb >= MEMORY_MIN && memoryMb <= MEMORY_MAX,
                "内存需在 128~65536 MB 之间");
        // 存活时间 [1, 1440]
        Assert.notNull(aliveMinutes, "容器存活时间不能为空");
        Assert.isTrue(aliveMinutes >= ALIVE_MIN && aliveMinutes <= ALIVE_MAX,
                "容器存活时间需在 1~1440 分钟之间");
        // 备注 ≤100
        Assert.isTrue(remark == null || remark.length() <= REMARK_MAX_LENGTH,
                "备注不超过 100 字");
        // 状态
        Assert.notNull(status, "沙箱状态不能为空");
    }

    /**
     * 保存 / 编辑沙箱（创建与编辑统一入口）。
     * <p>
     * 编辑时由应用层按当前状态 set 允许变更的字段（草稿 / 失败态全字段、其余仅备注）后调用本方法，
     * 整聚合覆盖落库。六步顺序：(1) 初始化审计字段 → (2) 无前置状态校验 → (3) 赋值（status 兜底 DRAFT +
     * num 生成）→ (4) 领域完整性校验 → (5) 持久化 → (6) 发布事件（每次 save 必发，按是否首次落库
     * 区分 CREATED / UPDATED）。
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. save 本身无前置状态约束

        // 3. 赋值：status 兜底 DRAFT；num 为空时经网关生成
        if (this.status == null) {
            this.status = SandboxStatus.DRAFT;
        }
        // 以主键判定是否首次落库，用于区分 CREATED / UPDATED 事件
        boolean isNew = this.getId() == null;
        if (StrUtil.isBlank(this.num)) {
            this.num = sandboxGateway.generateSandboxNum();
        }

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（upsert 语义）
        sandboxRepository.save(this);

        // 6. 发布事件：每次 save 必发，按首次 / 编辑区分类型
        publishEvent(isNew ? DomainEventConstant.SANDBOX_CREATED
                : DomainEventConstant.SANDBOX_UPDATED, operatorId, null);
    }

    /**
     * 逻辑删除沙箱。
     * <p>
     * 在线态禁删（应用层亦会提前拦截）；容器的销毁由 {@code SandboxRunner} 监听
     * {@code SANDBOX_DELETED} 事件后执行，本聚合只负责置软删标识 + 发事件。
     * 六步顺序：(1) 初始化 → (2) 校验 status!=ONLINE → (3) 置 deleted=1
     * → (4) 完整性校验 → (5) 软删 → (6) 发布 SANDBOX_DELETED。
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：在线态禁止删除（需先下线）
        Assert.isTrue(this.status != SandboxStatus.ONLINE, "请先下线后再删除");

        // 3. 赋值：置逻辑删除标识（容器 kill 由应用层监听事件处理）
        this.deleted = 1;

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化删除（infra deleteByNum 实现为软删 UPDATE deleted=1）
        sandboxRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SANDBOX_DELETED, operatorId, null);
    }

    // ---- 状态流转领域动作 ----

    /**
     * 提交：草稿 / 失败 → 初始化。
     * <p>
     * 仅置态 + 清空上次容器实例 + 发 {@code SANDBOX_SUBMITTED} 事件，<b>不</b>在此调 OpenSandbox；
     * 真正的容器创建由 {@code SandboxRunner} 监听该事件后异步执行。
     *
     * @param operatorId 操作人工号
     */
    public void submit(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅草稿 / 失败态可提交
        Assert.isTrue(this.status == SandboxStatus.DRAFT || this.status == SandboxStatus.FAILED,
                "仅草稿态或失败态可提交");
        // 3. 赋值：状态流转 + 清空上次容器实例
        this.status = SandboxStatus.INITIALIZED;
        this.sandboxInstanceId = null;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件（驱动应用层异步供给）
        publishEvent(DomainEventConstant.SANDBOX_SUBMITTED, operatorId, null);
    }

    /**
     * 上线：初始化 → 在线。
     * <p>
     * 由 {@code SandboxRunner} 在容器创建并健康检查通过后调用，带上容器实例 id 回写。
     *
     * @param sandboxInstanceId 已就绪的 OpenSandbox 容器实例 id
     * @param operatorId        操作人工号
     */
    public void online(String sandboxInstanceId, String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅初始化态可上线；实例 id 必填
        Assert.isTrue(this.status == SandboxStatus.INITIALIZED, "仅初始化态可上线");
        Assert.notBlank(sandboxInstanceId, "容器实例未创建，不能上线");
        // 3. 赋值：绑定实例 + 状态流转
        this.sandboxInstanceId = sandboxInstanceId;
        this.status = SandboxStatus.ONLINE;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件
        publishEvent(DomainEventConstant.SANDBOX_ONLINED, operatorId, null);
    }

    /**
     * 下线：在线 → 下线。
     * <p>
     * 容器的 kill 由 {@code SandboxRunner} 监听 {@code SANDBOX_OFFLINED} 事件后执行，
     * 本聚合只负责状态流转 + 发事件。
     *
     * @param operatorId 操作人工号
     */
    public void offline(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅在线态可下线
        Assert.isTrue(this.status == SandboxStatus.ONLINE, "仅在线态可下线");
        // 3. 赋值：状态流转
        this.status = SandboxStatus.OFFLINE;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件
        publishEvent(DomainEventConstant.SANDBOX_OFFLINED, operatorId, null);
    }

    /**
     * 重新上线：下线 → 初始化（重走供给流程）。
     * <p>
     * 与 {@link #submit(String)} 语义一致 —— 仅置初始化态 + 发 {@code SANDBOX_SUBMITTED}，
     * 真正建容器交由 {@code SandboxRunner} 异步供给。
     *
     * @param operatorId 操作人工号
     */
    public void reonline(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅下线态可重新上线
        Assert.isTrue(this.status == SandboxStatus.OFFLINE, "仅下线态可重新上线");
        // 3. 赋值：状态流转 + 清空上次容器实例
        this.status = SandboxStatus.INITIALIZED;
        this.sandboxInstanceId = null;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件（驱动应用层异步供给）
        publishEvent(DomainEventConstant.SANDBOX_SUBMITTED, operatorId, null);
    }

    /**
     * 标记初始化失败：初始化 → 失败。
     * <p>
     * 由 {@code SandboxRunner} 在容器创建 / 健康检查失败时调用；清空容器实例，
     * 失败原因随 {@code SANDBOX_PROVISION_FAILED} 事件载荷传出，供审计与前端提示。
     *
     * @param reason     失败原因
     * @param operatorId 操作人工号
     */
    public void markProvisionFailed(String reason, String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅初始化态可标记失败
        Assert.isTrue(this.status == SandboxStatus.INITIALIZED, "仅初始化态可标记失败");
        // 3. 赋值：状态流转 + 清空容器实例
        this.status = SandboxStatus.FAILED;
        this.sandboxInstanceId = null;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件（携带失败原因）
        publishEvent(DomainEventConstant.SANDBOX_PROVISION_FAILED, operatorId, reason);
    }

    /**
     * 脏态对账校正：在线态沙箱被判定底层容器已不存活时，流转为下线。
     * <p>
     * 容器是否存活的判定（调用 OpenSandbox）由应用层 {@code SandboxRunner} 完成，
     * 判定为不存活后调用本方法落库；本聚合不查询 OpenSandbox。
     *
     * @param operatorId 操作人工号（对账系统账号）
     */
    public void reconcileToOffline(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);
        // 2. 领域规则校验：仅在线态需要校正
        Assert.isTrue(this.status == SandboxStatus.ONLINE, "仅在线态可对账校正为下线");
        // 3. 赋值：状态流转
        this.status = SandboxStatus.OFFLINE;
        // 4. 领域完整性校验
        this.validate();
        // 5. 持久化
        sandboxRepository.save(this);
        // 6. 发布事件
        publishEvent(DomainEventConstant.SANDBOX_OFFLINED, operatorId, null);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人工号
     * @param failReason 失败原因（仅 PROVISION_FAILED 事件填充，其余传 null）
     */
    private void publishEvent(String type, String operatorId, String failReason) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(SandboxDomainEventDTO.from(this, operatorId, failReason))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
