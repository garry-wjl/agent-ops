package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 挂载 Agent 简表 DTO（application 层边界）。
 * <p>
 * 复用数下钻场景：列出挂载某工具的已发布 Agent（工具管理技术方案 §6.2 listMountedAgents）。
 * 仅承载定位与展示所需的最小字段。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentBriefDTO {

    /** Agent 业务编号（前缀 AGT）。 */
    private String num;

    /** Agent 名称。 */
    private String name;

    /** Agent 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    /** Agent 状态（PUBLISHED / OFFLINE 等）。 */
    private String status;
}
