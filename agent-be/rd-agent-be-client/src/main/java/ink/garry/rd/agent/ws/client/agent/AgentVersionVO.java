package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 版本列表项 VO（v3.0：含 DRAFT / PUBLISHED / ARCHIVED 三态）。
 * <p>
 * 前端版本管理 Tab 用 {@link #status} 字段分支渲染：
 * DRAFT 行展示 {@link #editorUserId} / {@link #lockUntil}；
 * PUBLISHED 行高亮当前在线版本；ARCHIVED 行可触发对比 / 回滚（v3.0 暂留 TODO）。
 */
@Data
public class AgentVersionVO {
    /** 版本业务编号 AVN... */
    private String num;
    /** 关联的 Agent 业务编号 */
    private String agentNum;
    /**
     * v3.0：版本状态 DRAFT / PUBLISHED / ARCHIVED。
     */
    private String status;
    /** 版本号 vX.Y.Z；DRAFT 时为 null */
    private String versionNum;
    /** 发布备注；DRAFT 时为 null */
    private String remark;
    /** 发布人 userId；DRAFT 时为 null */
    private String publishedBy;
    /** 发布时间；DRAFT 时为 null */
    private LocalDateTime publishedAt;
    /** 是否当前在线版本 */
    private Boolean current;
    /** v3.0：当前编辑者（仅 DRAFT 行展示） */
    private String editorUserId;
    /** v3.0：草稿编辑锁过期时间（仅 DRAFT 行展示） */
    private LocalDateTime lockUntil;
    /**
     * 本行版本对应的 ConfigSnapshot（v4.0：模型凭证已下沉模型管理，快照仅存 modelId 引用，无敏感字段无需脱敏）。
     * <p>
     * 前端版本管理 Tab 点击 [编辑] DRAFT 行时，直接以 versionList 中该行的 configSnapshot
     * 注入 CreateForm.initialSnapshot，避免再单独调 versionDetail。
     * <p>
     * 包含 v3.x 起纳入快照的 name / description / agentType（编辑表单 Step1 必读）；
     * v4.0 起含 modelId / toolNums / sandboxRef（资产引用）。
     */
    private Map<String, Object> configSnapshot;
}
