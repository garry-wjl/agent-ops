package ink.garry.rd.agent.ws.infra.session.factory;

import ink.garry.rd.agent.ws.domain.session.Session;
import ink.garry.rd.agent.ws.domain.session.factory.SessionFactory;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionCascadeGateway;
import ink.garry.rd.agent.ws.domain.session.gateway.SessionNumGateway;
import ink.garry.rd.agent.ws.domain.session.repository.MessageRepository;
import ink.garry.rd.agent.ws.domain.session.repository.SessionRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SessionFactory 实现：装配 Session 的全部基础设施依赖（Repository / Gateway / Publisher）。
 * <p>
 * v3.x 反转 §4.7：FactoryImpl 重新承担 {@link DomainEventPublisher} 注入，
 * application 层不再写 wire helper；工厂返回即「完整装配的聚合」。
 */
@Component
@RequiredArgsConstructor
public class SessionFactoryImpl implements SessionFactory {
    private final SessionRepository sessionRepository;
    private final SessionNumGateway sessionNumGateway;
    private final MessageRepository messageRepository;
    private final SessionCascadeGateway sessionCascadeGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Session createSession(String agentNum, String agentVersionNum, String skillHint, String creatorUserId,
                                 String title, String origin, String invokeContextJson) {
        Session session = new Session();
        session.setAgentNum(agentNum);
        session.setAgentVersionNum(agentVersionNum);
        session.setSkillHint(skillHint);
        session.setCreatorUserId(creatorUserId);
        session.setTitle(title);
        session.setOrigin(origin);
        session.setInvokeContextJson(invokeContextJson);
        return wire(session);
    }

    @Override
    public Session createByNum(String num) {
        return wire(sessionRepository.findByNum(num));
    }

    private Session wire(Session session) {
        if (session == null) {
            return null;
        }
        session.setSessionRepository(sessionRepository);
        session.setSessionNumGateway(sessionNumGateway);
        session.setMessageRepository(messageRepository);
        session.setSessionCascadeGateway(sessionCascadeGateway);
        session.setDomainEventPublisher(domainEventPublisher);
        return session;
    }
}

