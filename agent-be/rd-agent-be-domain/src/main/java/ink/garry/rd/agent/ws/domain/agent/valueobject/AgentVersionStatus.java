package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * Agent 版本状态枚举（v3.0：草稿合并到 agent_version 后的状态机）。
 * <p>
 * 同一 agentNum 下：
 * <ul>
 *   <li>{@link #DRAFT}：当前可编辑的草稿行；最多 1 行；version_num / change_level / remark / published_by /
 *       published_at 全为 NULL；current_flag=0；editor_user_id + lock_until 用于编辑锁。</li>
 *   <li>{@link #PUBLISHED}：当前在线版本；最多 1 行；current_flag=1；version_num 等已落值。</li>
 *   <li>{@link #ARCHIVED}：历史已发布版本；可有多行；current_flag=0；version_num 等保留。</li>
 * </ul>
 */
public enum AgentVersionStatus {
    /** 草稿态：可编辑；version_num/published_* 等为 NULL；同 agent 至多 1 行 */
    DRAFT,
    /** 当前在线版本（current_flag=1）；同 agent 至多 1 行 */
    PUBLISHED,
    /** 历史已发布版本（current_flag=0）；同 agent 可多行 */
    ARCHIVED
}
