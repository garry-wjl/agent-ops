package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 版本快照 DTO — application 层 {@code AgentQueryService.findCurrentVersionByAgentNum} 出参。
 * <p>
 * 用途:供 application 内部用例(非 Controller 直接出参)以及部分 application → application
 * 跨服务调用使用;Controller 出参请用 {@code client.agent.AgentVersionVO} / {@code AgentVersionDetailVO}。
 * <p>
 * <b>放在 client 而非 facade</b>:本 DTO 仅被 application / adapter 层消费,不被 infra 消费;
 * 详见 {@code docs/CODING-CONVENTIONS.md §3.1}。
 * <p>
 * <b>字段策略</b>:
 * <ul>
 *   <li>枚举字段以 String 形式承载 {@code name()},避免 client 层反向依赖 domain 枚举;</li>
 *   <li>{@code configSnapshotJson} 保持原始 JSON 字符串,由调用方按需 fastjson2 反序列化;</li>
 *   <li>{@code currentFlag} 沿用 0/1 整型,语义与 DB 一致(1=当前在线版本);</li>
 *   <li>不携带 {@code deleted} 逻辑删除位,DTO 仅暴露存活行。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentVersionDTO {

    /** 自增主键 */
    private Long id;

    /** 版本业务编号 AVN... */
    private String num;

    /** 关联 Agent 业务编号 */
    private String agentNum;

    /** 版本状态 DRAFT / PUBLISHED / ARCHIVED;取自 {@code AgentVersionStatus.name()} */
    private String status;

    /** 版本号字符串 vX.Y.Z;DRAFT 时为 null */
    private String versionNum;

    /** Semver major;DRAFT 时为 null */
    private Integer semverMajor;

    /** Semver minor;DRAFT 时为 null */
    private Integer semverMinor;

    /** Semver patch;DRAFT 时为 null */
    private Integer semverPatch;

    /**
     * ConfigSnapshot 原始 JSON 字符串(DRAFT 行也必填)。
     * <p>
     * 由调用方按需 fastjson2 反序列化为 ConfigSnapshot;
     * 调用方负责对 {@code modelApiKey} 等敏感字段脱敏。
     */
    private String configSnapshotJson;

    /** 发布备注(≥10 字符);DRAFT 时为 null */
    private String remark;

    /** 发布人 userId;DRAFT 时为 null */
    private String publishedBy;

    /** 发布时间;DRAFT 时为 null */
    private LocalDateTime publishedAt;

    /** 当前在线版本标记 0/1;仅 PUBLISHED 行为 1 */
    private Integer currentFlag;

    /** v3.0:当前编辑者 userId(仅 DRAFT 行使用) */
    private String editorUserId;

    /** v3.0:草稿编辑锁过期时间(仅 DRAFT 行使用) */
    private LocalDateTime lockUntil;

    /** 创建人 userId */
    private String createNo;

    /** 更新人 userId */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
