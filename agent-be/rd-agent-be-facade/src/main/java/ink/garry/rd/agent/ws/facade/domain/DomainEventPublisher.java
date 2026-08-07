package ink.garry.rd.agent.ws.facade.domain;

/**
 * 领域事件发布器接口（由 infra 层实现，基于 Spring ApplicationEventPublisher）。
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件。
     *
     * @param eventDTO 领域事件载荷，type 取自 {@link DomainEventConstant}
     */
    void send(DomainEventDTO eventDTO);
}
