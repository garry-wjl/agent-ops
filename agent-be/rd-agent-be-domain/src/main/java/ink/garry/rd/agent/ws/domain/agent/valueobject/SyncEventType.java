package ink.garry.rd.agent.ws.domain.agent.valueobject;

/**
 * A2A Agent Nacos 同步事件来源类型。
 * <p>
 * 用于审计 Agent 的最近一次同步是由哪个事件触发的，便于排错。
 */
public enum SyncEventType {
    /** Nacos 推送：新实例注册（首次发现） */
    INSTANCE_ADDED,
    /** Nacos 推送：实例元数据变更（含健康状态变化） */
    INSTANCE_CHANGED,
    /** Nacos 推送：实例下线（DEREGISTER） */
    INSTANCE_REMOVED,
    /** 兜底全量轮询（5min 一次）触发的对账更新 */
    POLLING_RECONCILE,
    /** 详情页「[手动重新同步]」按钮触发 */
    MANUAL_RESYNC
}
