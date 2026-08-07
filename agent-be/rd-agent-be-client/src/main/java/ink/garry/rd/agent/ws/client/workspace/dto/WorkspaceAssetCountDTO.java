package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作空间资产计数 DTO，用于删除空间前的「资产非空禁删」预检与错误体展示。
 * <p>tool 计数 S3 接入前恒为 0。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceAssetCountDTO {

    /** 空间内 Agent 数量。 */
    private Long agentCount;

    /** 空间内 Skill 数量。 */
    private Long skillCount;

    /** 空间内 Tool 数量（S3 接入前恒为 0）。 */
    private Long toolCount;

    /**
     * 是否存在任意资产。
     *
     * @return true=存在任一非 0 计数（禁止删除）
     */
    public boolean hasAnyAsset() {
        return nonZero(agentCount) || nonZero(skillCount) || nonZero(toolCount);
    }

    private static boolean nonZero(Long count) {
        return count != null && count > 0;
    }
}
