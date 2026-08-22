package ink.garry.rd.agent.ws.facade.domain;

/**
 * 领域事件类型常量。
 * 供各聚合发布 {@link DomainEventDTO} 时使用，命名严格遵循 {@code <聚合>_<动作>} 业务语义。
 * 与本类型对应的载荷请参见同名聚合下的 {@code XxxDomainEventDTO}（如 AgentDomainEventDTO）。
 */
public final class DomainEventConstant {
    private DomainEventConstant() {}

    /** Agent 创建完成 */
    public static final String AGENT_CREATED = "AGENT_CREATED";
    /** Agent 更新完成 */
    public static final String AGENT_UPDATED = "AGENT_UPDATED";
    /** Agent 删除完成 */
    public static final String AGENT_DELETED = "AGENT_DELETED";
    /** Agent 下线完成 */
    public static final String AGENT_OFFLINED = "AGENT_OFFLINED";
    /** Agent 草稿已保存 */
    public static final String AGENT_DRAFT_SAVED = "AGENT_DRAFT_SAVED";
    /** Agent 草稿已丢弃 */
    public static final String AGENT_DRAFT_DISCARDED = "AGENT_DRAFT_DISCARDED";
    /** Agent 版本发布完成 */
    public static final String AGENT_VERSION_PUBLISHED = "AGENT_VERSION_PUBLISHED";
    /** A2A Agent 完成一次 Nacos 同步（新增 / 元数据变更 / 实例下线 / 兜底轮询对账） */
    public static final String AGENT_A2A_SYNCED = "AGENT_A2A_SYNCED";
    /** Skill 版本发布完成 */
    public static final String SKILL_VERSION_PUBLISHED = "SKILL_VERSION_PUBLISHED";
    /** Skill 已废弃 */
    public static final String SKILL_DEPRECATED = "SKILL_DEPRECATED";
    /** 会话创建完成 */
    public static final String SESSION_CREATED = "SESSION_CREATED";
    /** 一次调用结束（成功 / 失败均计） */
    public static final String INVOCATION_FINISHED = "INVOCATION_FINISHED";
    /** 评测结束（成功 / 失败均计） */
    public static final String DATASET_PUBLISHED = "DATASET_PUBLISHED";
    public static final String EVAL_TASK_FINISHED = "EVAL_TASK_FINISHED";
    public static final String EVAL_TASK_FAILED = "EVAL_TASK_FAILED";
}
