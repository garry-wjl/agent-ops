package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 详情查询 DTO（对外详情查询用；与 {@code AgentDetailVO} 字段一一对应）。
 * <p>
 * 由 AgentQueryService 按 creationMode 分支装配（CONFIG 填 currentSnapshot / currentVersion /
 * hasDraft；A2A 填 a2aSource）；adapter 层经 assembler 转为 {@code AgentDetailVO} 出参。
 */
@Data
public class AgentDetailViewDTO {

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
    /** 状态 */
    private String status;
    /** 当前在线版本号 */
    private String currentVersionNum;
    /** 当前在线版本配置快照（已脱敏 Map；CONFIG 才有） */
    private Map<String, Object> currentSnapshot;
    /** 当前在线版本详情（CONFIG 才有） */
    private AgentVersionDetailViewDTO currentVersion;
    /** 是否存在草稿（CONFIG 才有意义） */
    private Boolean hasDraft;
    /** 草稿当前编辑者 */
    private String draftEditor;
    /** 草稿编辑锁过期时间 */
    private LocalDateTime draftLockUntil;
    /** A2A 来源信息（A2A 才有） */
    private A2aSourceViewDTO a2aSource;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 最后更新时间 */
    private LocalDateTime updateTime;
}
