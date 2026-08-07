package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 详情 VO。
 * <p>
 * CONFIG 模式：填充 currentSnapshot / hasDraft / draftEditor / draftLockUntil；
 * A2A 模式：填充 a2aSource，currentSnapshot / hasDraft 为 null。
 */
@Data
public class AgentDetailVO {
    /** Agent 业务编号 AGT... */
    private String num;
    /** Agent 显示名 */
    private String name;
    /** Agent 描述 */
    private String description;
    /** 业务标签（CONFIG / A2A 共用） */
    private List<String> tags;
    /** 创建方式 CONFIG / A2A */
    private String creationMode;
    /** 行为类型 NORMAL / SUPERVISOR / ROUTER */
    private String agentType;
    /** 负责人 userId */
    private String ownerUserId;
    /** 状态 DRAFT_ONLY / PUBLISHED / OFFLINE */
    private String status;
    /** 当前在线版本号（仅 CONFIG） */
    private String currentVersionNum;
    /** 当前版本配置 snapshot（仅 CONFIG）。**v2.8 留作兼容**；前端推荐用 {@link #currentVersion} 字段。 */
    private java.util.Map<String, Object> currentSnapshot;
    /**
     * 当前在线版本完整 VO（仅 CONFIG，含 versionNum / changeLevel / publishedBy / configSnapshot 等）。
     * <p>
     * v2.8 新增：前端各 Tab（基本信息 / 模型配置 / Skill / MCP / 高级配置）需要从中读取
     * configSnapshot 渲染；[+ 创建版本] 也基于 currentVersion.configSnapshot 创建草稿。
     * 状态为 DRAFT_ONLY（首次未发布）时该字段为 null。
     */
    private AgentVersionDetailVO currentVersion;
    /** 是否存在草稿（仅 CONFIG） */
    private Boolean hasDraft;
    /** 草稿编辑者（仅 hasDraft=true 时） */
    private String draftEditor;
    /** 草稿锁定到何时 */
    private LocalDateTime draftLockUntil;
    /** A2A 来源信息（仅 A2A 模式填充；CONFIG 为 null） */
    private A2aSourceVO a2aSource;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
