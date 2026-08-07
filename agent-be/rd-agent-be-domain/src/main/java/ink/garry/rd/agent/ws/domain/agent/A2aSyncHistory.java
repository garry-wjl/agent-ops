package ink.garry.rd.agent.ws.domain.agent;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.agent.repository.A2aSyncHistoryRepository;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;

import java.time.LocalDateTime;

/**
 * A2A Nacos 同步历史实体（v2.6 §6.2 第 4 节）。
 * <p>
 * 每次 A2A 同步事件（{@code AgentCommandService.createA2aFromNacos} / {@code syncByNum} /
 * {@code manualResync}：订阅推送 / 兜底轮询 / 手动重新同步）都向 {@code agent_a2a_sync_history}
 * 追加一行；前端「A2A 历史版本」列表反查本表。
 * <p>
 * 不继承 {@link ink.garry.rd.agent.ws.facade.domain.DomainEntity} —— 该实体仅追加，
 * 不包含 deleted / createNo / updateNo / updateTime 等通用审计字段，使用专用列
 * {@link #triggeredBy} 与 {@link #syncedAt} 表达「谁、何时、何事件触发」。
 */
public class A2aSyncHistory {

    /** 主键（持久化后回填） */
    private Long id;

    /** 所属 A2A Agent 业务编号；跨聚合引用 ID */
    private String agentNum;

    /** 本次同步获取到的远端版本号（取自 Agent Card version 字段，可空） */
    private String remoteVersion;

    /** 同步事件来源：INSTANCE_ADDED / INSTANCE_CHANGED / INSTANCE_REMOVED / POLLING_RECONCILE / MANUAL_RESYNC */
    private SyncEventType syncEventType;

    /** 触发人：订阅 / 兜底轮询固定 {@code nacos-sync}；手动重新同步为操作用户 userId */
    private String triggeredBy;

    /** 本次同步落地时的 Agent Card 完整 JSON（mediumtext），用于历史溯源 */
    private String agentCardJson;

    /** 同步发生时间 */
    private LocalDateTime syncedAt;

    /** 装配依赖：同步历史仓储 */
    private transient A2aSyncHistoryRepository repository;

    /** 默认无参构造（供框架反序列化使用） */
    public A2aSyncHistory() {}

    // ---- getters/setters（不使用 @Data，避免暴露 transient 仓储） ----

    /** @return 主键 */
    public Long getId() { return id; }

    /** @param id 主键 */
    public void setId(Long id) { this.id = id; }

    /** @return 所属 Agent num */
    public String getAgentNum() { return agentNum; }

    /** @param agentNum 所属 Agent num */
    public void setAgentNum(String agentNum) { this.agentNum = agentNum; }

    /** @return 远端版本号 */
    public String getRemoteVersion() { return remoteVersion; }

    /** @param remoteVersion 远端版本号 */
    public void setRemoteVersion(String remoteVersion) { this.remoteVersion = remoteVersion; }

    /** @return 同步事件来源 */
    public SyncEventType getSyncEventType() { return syncEventType; }

    /** @param syncEventType 同步事件来源 */
    public void setSyncEventType(SyncEventType syncEventType) { this.syncEventType = syncEventType; }

    /** @return 触发人 userId */
    public String getTriggeredBy() { return triggeredBy; }

    /** @param triggeredBy 触发人 userId */
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    /** @return Agent Card 完整 JSON */
    public String getAgentCardJson() { return agentCardJson; }

    /** @param agentCardJson Agent Card 完整 JSON */
    public void setAgentCardJson(String agentCardJson) { this.agentCardJson = agentCardJson; }

    /** @return 同步发生时间 */
    public LocalDateTime getSyncedAt() { return syncedAt; }

    /** @param syncedAt 同步发生时间 */
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    /** @param repository 仓储（由 Factory / RepositoryImpl 装配） */
    public void setRepository(A2aSyncHistoryRepository repository) { this.repository = repository; }

    /**
     * 持久化（仅 INSERT）：自动填充 {@link #syncedAt}，校验必填字段后落库。
     *
     * @param triggeredBy 触发人 userId
     */
    public void save(String triggeredBy) {
        if (this.triggeredBy == null) {
            this.triggeredBy = triggeredBy;
        }
        if (this.syncedAt == null) {
            this.syncedAt = LocalDateTime.now();
        }
        Assert.notBlank(this.agentNum, "agentNum 不能为空");
        Assert.notNull(this.syncEventType, "syncEventType 不能为空");
        Assert.notBlank(this.triggeredBy, "triggeredBy 不能为空");
        repository.save(this);
    }
}
