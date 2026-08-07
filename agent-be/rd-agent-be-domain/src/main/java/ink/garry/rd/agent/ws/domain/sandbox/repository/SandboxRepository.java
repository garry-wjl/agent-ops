package ink.garry.rd.agent.ws.domain.sandbox.repository;

import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;

/**
 * 沙箱聚合仓储接口。
 * <p>
 * 固定三方法契约（save / findByNum / deleteByNum）；其它读取（分页 / 详情 / 唯一性预检 /
 * 在线清单等）一律走应用层 QueryService 经 Mapper 只读查询，不在本接口扩张。
 */
public interface SandboxRepository {

    /**
     * 持久化 Sandbox 聚合（upsert 语义，不区分新增 / 更新）。
     *
     * @param aggregate 待保存的 Sandbox 聚合
     */
    void save(Sandbox aggregate);

    /**
     * 按业务编号加载 Sandbox 聚合。
     *
     * @param num 沙箱业务编号
     * @return Sandbox 聚合；不存在时返回 null
     */
    Sandbox findByNum(String num);

    /**
     * 按业务编号软删除 Sandbox（infra 实现为 UPDATE deleted=1）。
     *
     * @param num 沙箱业务编号
     */
    void deleteByNum(String num);
}
