package ink.garry.rd.agent.ws.application.common.listener;

import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工作空间领域事件监听器（本期空实现）。
 * <p>
 * 订阅 {@code WORKSPACE_CREATED / WORKSPACE_UPDATED / WORKSPACE_DELETED} 三个事件，本期仅打
 * INFO 日志；下期接入站内消息 / 审计时在此追加分支即可，不改 domain。
 * <p>
 * 事件由 {@code infra.common.event.CommonDomainEventPublisher} 经 Spring
 * {@code ApplicationEventPublisher} 发布为统一的 {@link DomainEventDTO}，本监听器按 type 过滤。
 */
@Slf4j
@Component
public class WorkspaceDomainEventListener {

    /** 本监听器关注的 workspace 事件类型集合。 */
    private static final Set<String> WORKSPACE_EVENT_TYPES = Set.of(
            DomainEventConstant.WORKSPACE_CREATED,
            DomainEventConstant.WORKSPACE_UPDATED,
            DomainEventConstant.WORKSPACE_DELETED);

    /**
     * 处理工作空间领域事件（本期仅日志）。
     *
     * @param event 领域事件；非 workspace 类型直接忽略
     */
    @EventListener
    public void onWorkspaceEvent(DomainEventDTO event) {
        if (event == null || !WORKSPACE_EVENT_TYPES.contains(event.getType())) {
            return;
        }
        log.info("[workspace-event] type={} id={} sender={} payload={}",
                event.getType(), event.getId(), event.getSender(), event.getData());
    }
}
