package ink.garry.rd.agent.ws.domain.session.gateway;

/**
 * 会话域编号生成网关：统一分配 session/message/invocationTrace 业务编号。
 * 实现位于 infra 层，可基于雪花/UUID/数据库序列等策略。
 */
public interface SessionNumGateway {
    /**
     * 生成新的会话业务编号。
     *
     * @return 全局唯一的 sessionNum
     */
    String generateSessionNum();

    /**
     * 生成新的消息业务编号。
     *
     * @return 全局唯一的 messageNum
     */
    String generateMessageNum();

    /**
     * 生成新的调用链记录业务编号。
     *
     * @return 全局唯一的 invocationTraceNum
     */
    String generateInvocationTraceNum();
}
