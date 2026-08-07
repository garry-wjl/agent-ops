package ink.garry.rd.agent.ws.client.workspace.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 编辑工作空间入参 Vo（整体覆盖：名称 + 描述 + 完整 adminEmpNos + 完整 memberEmpNos + 整空间用户-角色映射）。
 */
@Data
public class WorkspaceUpdateParam {

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
     * 整空间用户-角色映射（empNo → roleNum 列表），编辑空间抽屉「保存」时整体覆盖写。
     * <p>key 为成员工号；value 为该成员在本空间内绑定的全部角色 num（不含 platform 角色）。</p>
     * <p>未在 map 中出现的工号将被解除该空间下所有角色绑定（除非命中 SPACE_ADMIN 创建者保护）。</p>
     * <p>允许为空：缺省时 application 层跳过 {@code bindUserRoles} 调用，避免兼容性回归。</p>
     */
    private Map<String, List<String>> memberRoles;
}
