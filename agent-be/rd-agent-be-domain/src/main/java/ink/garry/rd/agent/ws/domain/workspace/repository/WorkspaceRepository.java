package ink.garry.rd.agent.ws.domain.workspace.repository;

import ink.garry.rd.agent.ws.domain.workspace.Workspace;

/**
 * 工作空间聚合仓储接口。
 * <p>
 * 仅暴露 save / findByNum / deleteByNum 三个命令路径方法；其它读取走 {@code WorkspaceReadGateway}。
 * adminList / memberList 由实现序列化为 admin_list / member_list 两个 JSON 列整体覆盖写。
 */
public interface WorkspaceRepository {

    /**
     * 持久化 Workspace 聚合（upsert 语义；admin_list / member_list JSON 列整体覆盖写）。
     *
     * @param aggregate 待保存的 Workspace 聚合
     */
    void save(Workspace aggregate);

    /**
     * 按业务编号加载 Workspace 聚合（JSON 列反序列化为两个 List&lt;String&gt;）。
     *
     * @param num 工作空间业务编号
     * @return Workspace 聚合；不存在时返回 null
     */
    Workspace findByNum(String num);

    /**
     * 按业务编号软删除 Workspace。
     *
     * @param num 工作空间业务编号
     */
    void deleteByNum(String num);
}
