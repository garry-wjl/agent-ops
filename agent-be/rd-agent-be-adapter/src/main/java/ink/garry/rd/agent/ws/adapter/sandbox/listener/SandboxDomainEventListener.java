package ink.garry.rd.agent.ws.adapter.sandbox.listener;

import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 沙箱领域事件监听入口（adapter 层）。
 * <p>
 * 与「listener 属 adapter 入站适配层」的架构约定一致（ARCHITECTURE.md §58 / §136）：本类只承担
 * <b>事件入口 + 异步分派</b>，接收 facade 的 {@link DomainEventDTO} 信封后挂
 * {@code sandboxProvisionExecutor} 线程池，委派到应用层 {@link SandboxRunner} 完成实际编排；
 * <b>不解析任何 domain 业务载荷、不写业务逻辑</b>（载荷 {@code SandboxDomainEventDTO} 的解析与容器编排
 * 全部在 {@link SandboxRunner} 内完成），从而 adapter 不接触 domain 业务类型。
 *
 * <h3>异步边界</h3>
 * 两个监听方法均挂 {@code @Async("sandboxProvisionExecutor")}，实现「提交 / 下线 / 删除命令立即返回、
 * 容器供给与销毁异步进行」。事件类型过滤由 {@link SandboxRunner#provision(DomainEventDTO)} /
 * {@link SandboxRunner#destroyContainer(DomainEventDTO)} 内部完成（非关注类型直接忽略），
 * 故本入口对每个事件无条件委派即可。
 */
@Slf4j
@Component
public class SandboxDomainEventListener {

    @Resource
    private SandboxRunner sandboxRunner;

    /**
     * 供给入口：监听 {@code SANDBOX_SUBMITTED}（提交 / 重新上线）后异步建容器。
     * <p>非关注类型由 {@link SandboxRunner#provision(DomainEventDTO)} 内部忽略。
     *
     * @param event 领域事件信封
     */
    @Async("sandboxProvisionExecutor")
    @EventListener
    public void onProvisionEvent(DomainEventDTO event) {
        sandboxRunner.provision(event);
    }

    /**
     * 销毁入口：监听 {@code SANDBOX_OFFLINED} / {@code SANDBOX_DELETED} 后异步 kill 容器。
     * <p>非关注类型由 {@link SandboxRunner#destroyContainer(DomainEventDTO)} 内部忽略。
     *
     * @param event 领域事件信封
     */
    @Async("sandboxProvisionExecutor")
    @EventListener
    public void onDestroyEvent(DomainEventDTO event) {
        sandboxRunner.destroyContainer(event);
    }
}
