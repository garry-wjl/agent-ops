package ink.garry.rd.agent.ws.domain.session.gateway;

/**
 * 会话级联清理网关：删除会话时由领域层调用，清理其下挂的子实体。
 * 实现位于 infra 层，使用 Mapper 直接批删，避免领域层耦合多聚合仓储。
 */
public interface SessionCascadeGateway {
    /**
     * 按会话编号批量删除其全部消息。
     *
     * @param sessionNum 会话业务编号
     */
    void deleteMessagesBySessionNum(String sessionNum);

    /**
     * 按会话编号批量删除其全部调用链记录。
     *
     * @param sessionNum 会话业务编号
     */
    void deleteTracesBySessionNum(String sessionNum);
}
