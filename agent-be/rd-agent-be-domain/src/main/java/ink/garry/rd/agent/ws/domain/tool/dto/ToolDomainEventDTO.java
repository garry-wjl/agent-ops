package ink.garry.rd.agent.ws.domain.tool.dto;

import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolStatus;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具领域事件载荷 POJO。
 * <p>
 * 用于 {@code DomainEventPublisher.send(DomainEventDTO)} 的 {@code data} 字段；
 * 订阅方按 {@code DomainEventConstant.TOOL_*} 事件类型解码使用（审计映射
 * TOOL_CREATE / TOOL_PUBLISH / TOOL_DEPRECATE / TOOL_DELETE_DRAFT）。
 * <p>
 * 放在 {@code domain.tool.dto} 子包下（与 SkillDomainEventDTO / SandboxDomainEventDTO 一致的
 * 本项目专项规范：事件载荷 DTO 集中在 dto/ 子包）；仅含属性，无业务逻辑。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolDomainEventDTO {

    /** 工具业务编号（MCP... / FC...）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 工具名称（事件发生时的快照）。 */
    private String name;

    /** 工具类型快照。 */
    private ToolType type;

    /** 创建方式快照。 */
    private CreationMode creationMode;

    /** 生命周期状态（事件发生后的状态）。 */
    private ToolStatus status;

    /** 标签快照。 */
    private List<String> tags;

    /** 负责人用户 ID。 */
    private String ownerUserId;

    /** 操作人用户 ID（用于审计与事件链路追溯）。 */
    private String operatorId;

    /** 事件实际发生时间（领域内时钟取 LocalDateTime.now()）。 */
    private LocalDateTime occurredAt;

    /**
     * 从 Tool 聚合根快照构造事件载荷。
     *
     * @param tool       Tool 聚合根
     * @param operatorId 操作人用户 ID
     * @return 已填充字段、可直接放入 {@code DomainEventDTO.data} 的事件载荷
     */
    public static ToolDomainEventDTO from(Tool tool, String operatorId) {
        return ToolDomainEventDTO.builder()
                .num(tool.getNum())
                .workspaceNum(tool.getWorkspaceNum())
                .name(tool.getName())
                .type(tool.getType())
                .creationMode(tool.getCreationMode())
                .status(tool.getStatus())
                .tags(tool.getTags())
                .ownerUserId(tool.getOwnerUserId())
                .operatorId(operatorId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
