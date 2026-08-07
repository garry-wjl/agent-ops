package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具详情 DTO（application 层边界）。
 * <p>
 * 本期详情即工具全字段 + reuseCount；以嵌套 {@link ToolDTO} 承载，预留后续扩展
 * （如挂载 Agent 列表内联）。复用数下钻的 Agent 列表由独立查询 {@code listMountedAgents} 提供。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolDetailDTO {

    /** 工具全字段快照（含 reuseCount / endpointMeta）。 */
    private ToolDTO tool;
}
