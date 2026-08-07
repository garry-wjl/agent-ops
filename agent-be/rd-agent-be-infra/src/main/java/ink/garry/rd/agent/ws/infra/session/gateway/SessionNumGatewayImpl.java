package ink.garry.rd.agent.ws.infra.session.gateway;

import ink.garry.rd.agent.ws.domain.session.gateway.SessionNumGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionNumGatewayImpl implements SessionNumGateway {
    private static final String SESSION_PREFIX = "SES";
    private static final String MESSAGE_PREFIX = "MSG";
    private static final String TRACE_PREFIX = "TRC";

    private final BizNumGenerator bizNumGenerator;

    @Override
    public String generateSessionNum() {
        return bizNumGenerator.generate(SESSION_PREFIX);
    }

    @Override
    public String generateMessageNum() {
        return bizNumGenerator.generate(MESSAGE_PREFIX);
    }

    @Override
    public String generateInvocationTraceNum() {
        return bizNumGenerator.generate(TRACE_PREFIX);
    }
}
