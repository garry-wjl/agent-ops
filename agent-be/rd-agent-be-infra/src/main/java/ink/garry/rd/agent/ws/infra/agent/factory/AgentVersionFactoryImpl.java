package ink.garry.rd.agent.ws.infra.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentVersionFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentVersionGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentVersionRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.Version;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AgentVersionFactory 实现：装配 Repository / Gateway / Publisher。
 * <p>
 * v3.x 反转 §4.7：FactoryImpl 重新承担 {@link DomainEventPublisher} 注入，
 * application 层不再写 wire helper。
 */
@Component
@RequiredArgsConstructor
public class AgentVersionFactoryImpl implements AgentVersionFactory {

    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionGateway agentVersionGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public AgentVersion create(String agentNum, Version version,
                               ConfigSnapshot snapshot, String remark, String publishedBy) {
        AgentVersion v = new AgentVersion();
        v.setAgentNum(agentNum);
        v.setVersion(version);
        v.setVersionNum(version == null ? null : version.toStr());
        v.setConfigSnapshot(snapshot);
        v.setRemark(remark);
        v.setPublishedBy(publishedBy);
        v.setCurrent(false);
        return wire(v);
    }

    @Override
    public AgentVersion createByNum(String num) {
        return wire(agentVersionRepository.findByNum(num));
    }

    private AgentVersion wire(AgentVersion v) {
        if (v == null) {
            return null;
        }
        v.setAgentVersionRepository(agentVersionRepository);
        v.setAgentVersionGateway(agentVersionGateway);
        v.setDomainEventPublisher(domainEventPublisher);
        return v;
    }
}
