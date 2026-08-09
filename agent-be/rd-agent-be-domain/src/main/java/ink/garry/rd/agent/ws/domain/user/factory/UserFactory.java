package ink.garry.rd.agent.ws.domain.user.factory;

import ink.garry.rd.agent.ws.domain.user.User;

/**
 * User 领域工厂。
 */
public interface UserFactory {

    /**
     * 新建未落库用户；密码经网关哈希；status=ENABLED；num 留空由 save 生成。
     *
     * @param username 登录用户名
     * @param email 邮箱
     * @param remark 备注（可空）
     * @param rawPassword 明文初始密码
     * @return 已装配依赖的聚合
     */
    User create(String username, String email, String remark, String rawPassword);

    /**
     * 按业务编号加载并装配协作依赖。
     *
     * @param num 用户业务编号
     * @return 聚合；不存在返回 null
     */
    User createByNum(String num);
}
