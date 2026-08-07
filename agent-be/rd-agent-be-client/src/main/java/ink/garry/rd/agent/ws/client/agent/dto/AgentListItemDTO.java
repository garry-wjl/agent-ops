package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 列表项查询 DTO（对外列表查询用；与 {@code AgentVO} 字段一一对应）。
 * <p>
 * 由 AgentQueryService 组装；adapter 层经 assembler 转为 {@code AgentVO} 出参。
 */
@Data
public class AgentListItemDTO {

    /** Agent 业务编号 AGT... */
    private String num;
    /** Agent 显示名 */
    private String name;
    /** Agent 描述 */
    private String description;
    /** 状态 DRAFT_ONLY / PUBLISHED / OFFLINE / PENDING_SYNC */
    private String status;
    /** Skill 数量 */
    private Integer skillNum;
    /** Skill 名称列表 */
    private List<String> skillNames;
    /** 来源标识 MANUAL / NACOS（派生字段，不入库） */
    private String agentSource;
    /** 创建方式 CONFIG / A2A */
    private String creationMode;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 最后更新时间 */
    private LocalDateTime updateTime;
}
