package ink.garry.rd.agent.ws.client.workspace.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建工作空间入参 Vo（adapter 层用，来自 HTTP 请求体）。
 * <p>创建人自动进入 adminList，无需在 initialAdminEmpNos 重复传入。
 * <p>v2：支持 memberRoles 按角色批量绑人（替代 initialAdminEmpNos / initialMemberEmpNos）。
 */
@Data
public class WorkspaceCreateParam {

    /** 空间名称（必填，1~64 字符）。 */
    private String name;

    /** 空间描述（可空，≤200 字符）。 */
    private String description;

    /** 初始管理员工号列表（可空；v2 推荐用 memberRoles 替代）。 */
    private List<String> initialAdminEmpNos;

    /** 初始成员工号列表（可空；v2 推荐用 memberRoles 替代）。 */
    private List<String> initialMemberEmpNos;

    /**
     * 整空间用户-角色映射（roleNum → empNo 列表），创建时按角色批量绑人。
     * <p>创建人自动绑定 RL-SPACE-ADMIN（前端必在 map 中携带创建人）。</p>
     * <p>缺省时保留旧路径（initialAdminEmpNos / initialMemberEmpNos）。</p>
     */
    private Map<String, List<String>> memberRoles;
}
