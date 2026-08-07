package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A2A Nacos 同步历史 VO（v2.6）。
 * <p>
 * A2A Agent 详情页「历史版本」列表使用：每行对应一次 A2A 同步事件落库记录
 * （来源：syncByNum / createA2aFromNacos / manualResync）。
 */
@Data
public class A2aSyncHistoryVO {

    /** 历史记录主键 id（持久化后回填，前端用于 stable rowKey） */
    private Long id;

    /** 远端版本号（取自 Agent Card version 字段，可空） */
    private String remoteVersion;

    /** 同步事件来源：INSTANCE_ADDED / INSTANCE_CHANGED / INSTANCE_REMOVED / POLLING_RECONCILE / MANUAL_RESYNC */
    private String syncEventType;

    /** 触发人：订阅 / 兜底轮询固定 nacos-sync；手动重新同步为操作用户 userId */
    private String triggeredBy;

    /** 同步发生时间 */
    private LocalDateTime syncedAt;

    /** 本次同步落地时的 Agent Card 完整 JSON（前端 hover/弹层展示） */
    private String agentCardJson;
}
