package ink.garry.rd.agent.ws.domain.sandbox.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRepository;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Sandbox 领域工厂。
 * <p>
 * 提供 2 个 build 方法，覆盖 Sandbox 的两种构建场景（与 {@code WorkspaceFactory} /
 * {@code SkillFactory} 风格一致）：
 * <ul>
 *   <li>{@link #buildSandbox}：用必要字段构造一条新的 Sandbox（未落库）；status 由
 *       {@link Sandbox#save(String)} 在为空时兜底为 {@code DRAFT}，num 由 save 在为空时经网关生成。</li>
 *   <li>{@link #buildSandboxByNum}：按业务编号从仓储加载 Sandbox 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>：本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。
 * 创建出的 Sandbox 由工厂手动 wire 所需的 Repository / Gateway / EventPublisher，
 * 使调用方可直接执行业务方法（save / submit / online / offline / delete 等）。
 */
@Component
public class SandboxFactory {

    @Resource
    private SandboxRepository sandboxRepository;
    @Resource
    private SandboxGateway sandboxGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要字段构造一条新的 Sandbox 聚合（未落库）。
     * <p>
     * 仅接收创建期用户可填业务字段；status / num / sandboxInstanceId / 审计字段不在此处赋值，
     * 由 {@link Sandbox#save(String)} 统一处理。调用方拿到返回的 Sandbox 后通常立即
     * 调用 {@link Sandbox#save(String)} 完成首次落库（草稿态）。
     *
     * @param workspaceNum 归属工作空间业务编号
     * @param name         沙箱名称
     * @param type         沙箱类型（本期固定 {@link SandboxType#CODE}）
     * @param cpu          CPU 核数（0.5 步进）
     * @param memoryMb     内存大小（MB）
     * @param aliveMinutes 容器存活时间（分钟）
     * @param remark       备注（可空，≤100 字）
     * @return 已装配完依赖、可直接 save 的 Sandbox 聚合
     */
    public Sandbox buildSandbox(String workspaceNum,
                                String name,
                                SandboxType type,
                                BigDecimal cpu,
                                Integer memoryMb,
                                Integer aliveMinutes,
                                String remark) {
        Assert.notBlank(workspaceNum, "归属工作空间编号不能为空");
        Assert.notBlank(name, "沙箱名称不能为空");
        Assert.notNull(type, "沙箱类型不能为空");

        return new Sandbox(workspaceNum, name, type, cpu, memoryMb, aliveMinutes, remark,
                sandboxRepository, sandboxGateway, domainEventPublisher);
    }

    /**
     * 按业务编号加载 Sandbox 并装配依赖（等价于 {@code sandboxRepository.findByNum(num)} + wire）。
     *
     * @param num 沙箱业务编号
     * @return 装配完依赖的 Sandbox 聚合；不存在时返回 {@code null}
     */
    public Sandbox buildSandboxByNum(String num) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        Sandbox sandbox = sandboxRepository.findByNum(num);
        if (sandbox == null) {
            return null;
        }
        wireSandbox(sandbox);
        return sandbox;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Sandbox 聚合根。 */
    private void wireSandbox(Sandbox sandbox) {
        sandbox.setSandboxRepository(this.sandboxRepository);
        sandbox.setSandboxGateway(this.sandboxGateway);
        sandbox.setDomainEventPublisher(this.domainEventPublisher);
    }
}
