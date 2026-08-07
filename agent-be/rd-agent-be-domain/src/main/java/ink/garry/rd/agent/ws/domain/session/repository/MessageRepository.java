package ink.garry.rd.agent.ws.domain.session.repository;

import ink.garry.rd.agent.ws.domain.session.Message;

/**
 * Message 实体仓储：仅承担命令侧持久化与按编号读取。
 */
public interface MessageRepository {
    /**
     * 新增或更新消息。
     *
     * @param entity 待保存的消息实体
     */
    void save(Message entity);

    /**
     * 按业务编号加载消息。
     *
     * @param num 消息业务编号
     * @return 实体；不存在时返回 null
     */
    Message findByNum(String num);

    /**
     * 按业务编号删除（软删/物理删由实现决定）。
     *
     * @param num 消息业务编号
     */
    void deleteByNum(String num);
}
