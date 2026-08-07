package ink.garry.rd.agent.ws.domain.session.gateway;

import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.Session;

import java.util.List;

/**
 * 会话域只读查询网关：承接非命令场景的列表/分页/明细读取，
 * 与仓储分离以保持仓储仅负责 save/findByNum/deleteByNum。
 */
public interface SessionReadGateway {
    /**
     * 按创建人 + Agent 维度分页查询会话。
     *
     * @param creatorUserId 会话归属人 ID（必填）
     * @param agentNum      Agent 编号筛选，可空
     * @param pageNo        页码，从 1 开始
     * @param pageSize      每页大小
     * @return 分页结果（total + list）
     */
    PageResult<Session> pageQuery(String creatorUserId, String agentNum, Integer pageNo, Integer pageSize);

    /**
     * 拉取指定会话的最近若干条消息（按时间正序或倒序由实现决定）。
     *
     * @param sessionNum 会话业务编号
     * @param limit      最大条数限制
     * @return 消息列表
     */
    List<Message> listMessages(String sessionNum, Integer limit);

    /**
     * 通用分页结果记录：总数 + 列表。
     */
    record PageResult<T>(Long total, List<T> list) {}
}
