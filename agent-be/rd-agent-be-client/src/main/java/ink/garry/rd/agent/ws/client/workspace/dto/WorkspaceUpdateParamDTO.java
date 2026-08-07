package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 编辑工作空间入参 DTO（整体覆盖：名称 + 描述 + 完整 adminList + 完整 memberList + 整空间用户-角色映射）。
 * <p>成员的添加 / 移除 / 升降级都通过提交完整的 adminEmpNos / memberEmpNos 完成。
 */
@Data
public class WorkspaceUpdateParamDTO {

    /** 工作空间业务编号（必填）。 */
    private String num;

    /** 空间名称（必填，1~64 字符）。 */
    private String name;

    /** 空间描述（可空，≤200 字符）。 */
    private String description;

    /** 完整管理员工号列表（必填，至少 1 人）。 */
    private List<String> adminEmpNos;

    /** 完整普通成员工号列表（可空）。 */
    private List<String> memberEmpNos;

    /**
     * 整空间用户-角色映射（empNo → roleNum 列表，编辑空间「保存」时整体覆盖写）。
     * 缺省（null）时跳过 bindUserRoles 调用，保持与旧接口兼容。
     */
    private Map<String, List<String>> memberRoles;
}
