package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 版本查询 DTO（对外版本列表查询用；与 {@code AgentVersionVO} 字段一一对应）。
 * <p>
 * 区别于内部 {@code AgentVersionDTO}（Runner / Session 用的「全字段 + JSON 原样透传」版）：
 * 本 DTO 面向对外查询，{@code configSnapshot} 已 Map 化并脱敏 modelApiKey。
 */
@Data
public class AgentVersionViewDTO {

    /** 版本业务编号 AVN... */
    private String num;
    /** 关联 Agent 业务编号 */
    private String agentNum;
    /** 版本状态 DRAFT / PUBLISHED / ARCHIVED */
    private String status;
    /** 版本号 vX.Y.Z；DRAFT 为 null */
    private String versionNum;
    /** 发布备注 */
    private String remark;
    /** 发布人 userId */
    private String publishedBy;
    /** 发布时间 */
    private LocalDateTime publishedAt;
    /** 是否当前在线版本 */
    private Boolean current;
    /** 当前编辑者（仅 DRAFT 行） */
    private String editorUserId;
    /** 草稿编辑锁过期时间（仅 DRAFT 行） */
    private LocalDateTime lockUntil;
    /** 配置快照（已脱敏 modelApiKey 的 Map） */
    private Map<String, Object> configSnapshot;
}
