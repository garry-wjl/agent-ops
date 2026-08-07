package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * 挂载 Agent 简表 Vo（adapter 层出参）。
 * <p>
 * 复用数下钻场景：列出挂载某工具的已发布 Agent（工具管理技术方案 §7.3 mountedAgents）；
 * 由 application 的 {@code AgentBriefDTO} 经 {@code ToolVoAssembler} 转换而来。
 */
@Data
public class AgentBriefVo {

    /** Agent 业务编号（前缀 AGT）。 */
    private String num;

    /** Agent 名称。 */
    private String name;

    /** Agent 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    /** Agent 状态（PUBLISHED / OFFLINE 等）。 */
    private String status;
}
