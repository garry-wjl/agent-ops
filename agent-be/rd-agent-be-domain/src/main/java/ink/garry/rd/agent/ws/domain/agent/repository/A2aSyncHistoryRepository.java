package ink.garry.rd.agent.ws.domain.agent.repository;

import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;

import java.util.List;

/**
 * A2A 同步历史仓储接口（v2.6）。
 * <p>
 * 该实体仅追加，不修改不删除（保留 100 条上限由 application 层调用 {@link #purgeOldest} 维护）。
 * 不沿用通用 {@code save / findByNum / deleteByNum} 三方法约定 —— 同步历史无业务编号，
 * 删除策略也按「按 agentNum 保留 N 条」执行而非按 num 单条删除。
 */
public interface A2aSyncHistoryRepository {

    /**
     * 追加一条同步历史（仅 INSERT）。
     *
     * @param history 待保存的历史项
     */
    void save(A2aSyncHistory history);

    /**
     * 按 agentNum 倒序列出最近 limit 条同步历史。
     *
     * @param agentNum A2A Agent 业务编号
     * @param limit    返回条数上限
     * @return 倒序的历史列表（最新在前）
     */
    List<A2aSyncHistory> listByAgentNum(String agentNum, int limit);

    /**
     * 保留 keepCount 条最新历史，删除其余旧条目（物理删除）。
     *
     * @param agentNum  A2A Agent 业务编号
     * @param keepCount 保留条数（默认 100）
     */
    void purgeOldest(String agentNum, int keepCount);
}
