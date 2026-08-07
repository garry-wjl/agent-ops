package ink.garry.rd.agent.ws.infra.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.agent.A2aSyncHistory;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A2A 同步历史持久化实体（对应表 agent_a2a_sync_history，v2.6）。
 */
@Data
@TableName("agent_a2a_sync_history")
public class A2aSyncHistoryEntity {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 A2A Agent 业务编号 */
    @TableField("agent_num")
    private String agentNum;

    /** 远端版本号（取自 Agent Card version 字段，可空） */
    @TableField("remote_version")
    private String remoteVersion;

    /** 同步事件来源（枚举名） */
    @TableField("sync_event_type")
    private String syncEventType;

    /** 触发人 userId（订阅 / 兜底轮询固定 nacos-sync） */
    @TableField("triggered_by")
    private String triggeredBy;

    /** 同步落地时的 Agent Card 完整 JSON */
    @TableField("agent_card_json")
    private String agentCardJson;

    /** 同步发生时间 */
    @TableField("synced_at")
    private LocalDateTime syncedAt;

    /**
     * Entity → Domain。
     *
     * @param e 持久化实体
     * @return 领域实体；e 为 null 返回 null
     */
    public static A2aSyncHistory toDomain(A2aSyncHistoryEntity e) {
        if (e == null) {
            return null;
        }
        A2aSyncHistory h = new A2aSyncHistory();
        h.setId(e.getId());
        h.setAgentNum(e.getAgentNum());
        h.setRemoteVersion(e.getRemoteVersion());
        h.setSyncEventType(e.getSyncEventType() == null ? null : SyncEventType.valueOf(e.getSyncEventType()));
        h.setTriggeredBy(e.getTriggeredBy());
        h.setAgentCardJson(e.getAgentCardJson());
        h.setSyncedAt(e.getSyncedAt());
        return h;
    }

    /**
     * Domain → Entity。
     *
     * @param h 领域实体
     * @return 持久化实体
     */
    public static A2aSyncHistoryEntity fromDomain(A2aSyncHistory h) {
        A2aSyncHistoryEntity e = new A2aSyncHistoryEntity();
        e.setId(h.getId());
        e.setAgentNum(h.getAgentNum());
        e.setRemoteVersion(h.getRemoteVersion());
        e.setSyncEventType(h.getSyncEventType() == null ? null : h.getSyncEventType().name());
        e.setTriggeredBy(h.getTriggeredBy());
        e.setAgentCardJson(h.getAgentCardJson());
        e.setSyncedAt(h.getSyncedAt());
        return e;
    }
}
