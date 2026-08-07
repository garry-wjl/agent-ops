package ink.garry.rd.agent.ws.domain.auth.role.repository;

import ink.garry.rd.agent.ws.domain.auth.role.Role;

/**
 * Role 聚合仓储接口。
 * <p>仅暴露 save / findByNum / deleteByNum 三命令路径方法；其余读取走 Mapper（application 直接注入）。</p>
 */
public interface RoleRepository {

    /**
     * 持久化 Role 聚合（upsert 语义；permission_codes JSON 列整体覆盖写）。
     *
     * @param aggregate 待保存的 Role
     */
    void save(Role aggregate);

    /**
     * 按业务编号加载 Role 聚合。
     *
     * @param num 角色业务编号
     * @return Role；不存在时返回 null
     */
    Role findByNum(String num);

    /**
     * 按业务编号软删除 Role。
     *
     * @param num 角色业务编号
     */
    void deleteByNum(String num);
}
