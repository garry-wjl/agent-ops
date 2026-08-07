package ink.garry.rd.agent.ws.infra.session.factory;

import ink.garry.rd.agent.ws.domain.session.InvocationTrace;
import ink.garry.rd.agent.ws.domain.session.factory.InvocationTraceFactory;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionNumGateway;
import ink.garry.rd.agent.ws.domain.session.repository.InvocationTraceRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * InvocationTraceFactory 实现：装配 Repository / NumGateway / Publisher。
 * <p>
 * v3.x 反转 §4.7：FactoryImpl 重新承担 {@link DomainEventPublisher} 注入，
 * application 层不再写 wire helper。
 */
@Component
@RequiredArgsConstructor
public class InvocationTraceFactoryImpl implements InvocationTraceFactory {

    private final InvocationTraceRepository invocationTraceRepository;
    private final SessionNumGateway sessionNumGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public InvocationTrace create() {
        InvocationTrace trace = new InvocationTrace();
        trace.setNum(sessionNumGateway.generateInvocationTraceNum());
        trace.setInvocationTraceRepository(invocationTraceRepository);
        trace.setDomainEventPublisher(domainEventPublisher);
        return trace;
    }
}
