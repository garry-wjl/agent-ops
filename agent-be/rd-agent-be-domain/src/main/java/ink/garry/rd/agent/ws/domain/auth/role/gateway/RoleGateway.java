package ink.garry.rd.agent.ws.domain.auth.role.gateway;

import ink.garry.rd.agent.ws.domain.auth.RoleScope;

/**
 * Role 领域网关。
 * <p>定义在 domain，实现在 infra。聚合根装配后通过本接口生成业务编号 / 名称唯一性预检 / 绑定数统计。</p>
 */
public interface RoleGateway {

    /**
     * 生成角色业务编号。
     *
     * @param scope        作用域；决定前缀（RL-PLATFORM- / RL-SPACE-）
     * @param workspaceNum 空间编号；仅供 infra 校验/打日志，不参与编号生成
     * @return 完整业务编号
     */
    String generateRoleNum(RoleScope scope, String workspaceNum);

    /**
     * 角色名空间内唯一性预检。
     *
     * @param scope          作用域
     * @param workspaceNum   空间编号（scope=PLATFORM 时传 null）
     * @param name           待校验角色名
     * @param excludeRoleNum 编辑场景下排除自身的 roleNum，新建场景传 null
     * @return 已存在同名角色时返回 true，否则 false
     */
    boolean isNameDuplicate(RoleScope scope, String workspaceNum, String name, String excludeRoleNum);

    /**
     * 统计角色当前绑定的用户数量（删除前校验 + 列表展示）。
     *
     * @param roleNum 角色业务编号
     * @return 绑定用户数量
     */
    long countAssignedUsers(String roleNum);
}
