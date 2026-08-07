package ink.garry.rd.agent.ws.domain.model.repository;

import ink.garry.rd.agent.ws.domain.model.Model;

/**
 * 模型聚合仓储接口。
 * <p>
 * 固定三方法契约（save / findByNum / deleteByNum）；其它读取（分页 / 详情 / 唯一性预检等）
 * 一律走应用层 QueryService 经 Mapper 只读查询，不在本接口扩张。
 * <p>
 * 实现（{@code ModelRepositoryImpl}）在 Entity ↔ 领域对象映射时完成 apiKey 明文 ↔ 密文转换，
 * 领域对象边界内 {@code Model.apiKey} 始终为明文。
 */
public interface ModelRepository {

    /**
     * 持久化 Model 聚合（upsert 语义，不区分新增 / 更新）。
     *
     * @param aggregate 待保存的 Model 聚合
     */
    void save(Model aggregate);

    /**
     * 按业务编号加载 Model 聚合。
     *
     * @param num 模型业务编号
     * @return Model 聚合；不存在时返回 null
     */
    Model findByNum(String num);

    /**
     * 按业务编号软删除 Model（infra 实现为 UPDATE deleted=1）。
     *
     * @param num 模型业务编号
     */
    void deleteByNum(String num);
}
