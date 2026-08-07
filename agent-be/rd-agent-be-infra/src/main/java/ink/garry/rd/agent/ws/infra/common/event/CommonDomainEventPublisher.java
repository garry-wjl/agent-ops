package ink.garry.rd.agent.ws.infra.common.event;

import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器 - 基于 Spring ApplicationEventPublisher 的本地实现。
 * <p>
 * 后续若需跨进程，替换为 RocketMQ / Kafka 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void send(DomainEventDTO event) {
        if (event == null) {
            return;
        }
        log.info("publish domain event type={} id={} sender={}", event.getType(), event.getId(), event.getSender());
        applicationEventPublisher.publishEvent(event);
    }
}
