package ink.garry.rd.agent.ws.domain.session.factory;

import ink.garry.rd.agent.ws.domain.session.Session;

/**
 * Session 聚合工厂：构造装配完整依赖的 Session 实例。
 */
public interface SessionFactory {
    /**
     * 创建一个全新的会话（尚未落库），分配业务编号在 save 时完成。
     *
     * @param agentNum           所绑定的 Agent 编号
     * @param agentVersionNum    所绑定的 Agent 版本编号
     * @param skillHint          Skill 提示，可空
     * @param creatorUserId      会话创建人用户 ID
     * @param title              会话标题，可空（系统生成）
     * @param origin             会话来源：DEBUG_CONSOLE / API
     * @param invokeContextJson  会话默认调用上下文 JSON object，可空
     * @return 已装配依赖的 Session 实例
     */
    Session createSession(String agentNum, String agentVersionNum, String skillHint, String creatorUserId,
                          String title, String origin, String invokeContextJson);

    /**
     * 按业务编号加载会话并装配依赖；不存在时返回 null。
     *
     * @param num 会话业务编号
     * @return 装配完依赖的 Session 实例，或 null
     */
    Session createByNum(String num);
}
