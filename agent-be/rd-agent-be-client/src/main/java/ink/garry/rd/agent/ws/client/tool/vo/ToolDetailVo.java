package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * 工具详情 Vo（adapter 层出参）。
 * <p>
 * 本期详情即工具全字段 + reuseCount，以嵌套 {@link ToolVo} 承载；
 * 由 application 的 {@code ToolDetailDTO} 经 {@code ToolVoAssembler} 转换而来。
 */
@Data
public class ToolDetailVo {

    /** 工具全字段快照（含 reuseCount / endpointMeta）。 */
    private ToolVo tool;
}
