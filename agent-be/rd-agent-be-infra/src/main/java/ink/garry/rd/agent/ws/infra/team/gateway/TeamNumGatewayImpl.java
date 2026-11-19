package ink.garry.rd.agent.ws.infra.team.gateway;

import ink.garry.rd.agent.ws.domain.team.gateway.TeamNumGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 用统一编号器生成团队和运行编号。 */
@Component
public class TeamNumGatewayImpl implements TeamNumGateway {

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String nextTeamNum() {
        return bizNumGenerator.generate("ATM");
    }

    @Override
    public String nextRunNum() {
        return bizNumGenerator.generate("ATR");
    }
}
