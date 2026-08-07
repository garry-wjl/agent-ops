package ink.garry.rd.agent.ws.domain.session.repository;

import ink.garry.rd.agent.ws.domain.session.Session;

/**
 * Session 聚合根仓储：仅承担命令侧持久化与按编号读取。
 */
public interface SessionRepository {
    /**
     * 新增或更新会话聚合根。
     *
     * @param aggregate 待保存的会话
     */
    void save(Session aggregate);

    /**
     * 按业务编号加载会话。
     *
     * @param num 会话业务编号
     * @return 实体；不存在时返回 null
     */
    Session findByNum(String num);

    /**
     * 按业务编号删除会话（不级联子实体，级联由 SessionCascadeGateway 完成）。
     *
     * @param num 会话业务编号
     */
    void deleteByNum(String num);
}
