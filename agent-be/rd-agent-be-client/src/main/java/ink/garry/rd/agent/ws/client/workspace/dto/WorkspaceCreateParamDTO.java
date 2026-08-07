package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建工作空间入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>创建人自动进入 adminList，无需在 initialAdminEmpNos 重复传入。
 * <p>v2：支持 memberRoles 按角色批量绑人。
 */
@Data
public class WorkspaceCreateParamDTO {

    /** 空间名称（必填，1~64 字符）。 */
    private String name;

    /** 空间描述（可空，≤200 字符）。 */
    private String description;

    /** 初始管理员工号列表（可空；不含创建人时由工厂补入）。 */
    private List<String> initialAdminEmpNos;

    /** 初始成员工号列表（可空）。 */
    private List<String> initialMemberEmpNos;

    /**
     * 整空间用户-角色映射（roleNum → empNo 列表），创建时按角色批量绑人。
     * <p>缺省时保留旧路径。</p>
     */
    private Map<String, List<String>> memberRoles;
}
