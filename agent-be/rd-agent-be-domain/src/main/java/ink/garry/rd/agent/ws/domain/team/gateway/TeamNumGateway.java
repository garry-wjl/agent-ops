package ink.garry.rd.agent.ws.domain.team.gateway;

/**
 * Agent Team 业务编号。
 */
public interface TeamNumGateway {

    /** @return 团队编号，前缀 ATM */
    String nextTeamNum();

    /** @return 运行编号，前缀 ATR */
    String nextRunNum();
}
