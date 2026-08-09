package ink.garry.rd.agent.ws.domain.user.repository;

import ink.garry.rd.agent.ws.domain.user.User;

/**
 * User 聚合仓储（仅三方法契约）。
 */
public interface UserRepository {

    /**
     * 持久化聚合（upsert）。
     *
     * @param aggregate 用户聚合
     */
    void save(User aggregate);

    /**
     * 按业务编号加载聚合（不装配协作依赖）。
     *
     * @param num 用户业务编号
     * @return 聚合；不存在返回 null
     */
    User findByNum(String num);

    /**
     * 按业务编号软删除。
     *
     * @param num 用户业务编号
     */
    void deleteByNum(String num);
}
