package ink.garry.rd.agent.ws.domain.agent.gateway;

/**
 * Agent 草稿编辑分布式锁网关（按 agentNum 维度加锁，避免多人并发编辑同一 Agent 的草稿版本）。
 * <p>
 * v3.0：草稿合并到 {@code agent_version.status=DRAFT} 行后，本网关命名去掉 {@code AgentDraft} 前缀，
 * 强调"按 agentNum 加锁的通用编辑锁"，与具体实体解耦；DB 锁字段
 * （{@code agent_version.editor_user_id / lock_until}）作为持久化兜底，本网关走 Redis 热路径。
 */
public interface DraftLockGateway {

    /**
     * 尝试为某 Agent 的草稿编辑加锁。
     * 进入草稿编辑页 / 自动保存前调用，避免多人并发覆盖。
     *
     * @param agentNum   Agent 业务编号
     * @param editorId   尝试加锁的用户
     * @param ttlSeconds 锁定时长（秒）
     * @return true=加锁成功；false=已被他人锁定
     */
    boolean tryLock(String agentNum, String editorId, long ttlSeconds);

    /**
     * 释放锁（仅当锁持有者 == editorId）。
     * 主动退出编辑页或发布完成后调用，避免长时间空占。
     *
     * @param agentNum Agent 业务编号
     * @param editorId 锁持有者 userId
     */
    void unlock(String agentNum, String editorId);

    /**
     * 当前锁持有者 userId，无锁返回 null。
     * 用于在 tryLock 失败时回显"当前由谁在编辑"。
     *
     * @param agentNum Agent 业务编号
     * @return 持有者 userId；无锁时为 null
     */
    String currentHolder(String agentNum);
}
