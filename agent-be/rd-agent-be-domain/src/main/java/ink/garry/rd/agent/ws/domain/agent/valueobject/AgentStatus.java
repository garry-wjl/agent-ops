package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * Agent 生命周期状态枚举（详见技术方案 §3.2 状态机；v2.6 起新增 PENDING_SYNC）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>CONFIG：DRAFT_ONLY → PUBLISHED → OFFLINE → 删除。</li>
 *   <li>A2A：DRAFT_ONLY（接入草稿） → PENDING_SYNC（[确认接入] 后等待 Nacos 首次同步）
 *       → PUBLISHED（首次同步 healthy=true）/ OFFLINE（首次同步 healthy=false）。</li>
 * </ul>
 */
public enum AgentStatus {
    /** 仅有草稿（首次未发布）；不出现在挂载下拉与对外列表 */
    DRAFT_ONLY,
    /** A2A 已确认接入但 Nacos 尚未首次回写：等待订阅推送或兜底轮询同步（v2.6） */
    PENDING_SYNC,
    /** 已发布（在线可用）；存在 currentVersionNum */
    PUBLISHED,
    /** 已下线；保留历史，可再次发布转回 PUBLISHED 或删除 */
    OFFLINE
}
