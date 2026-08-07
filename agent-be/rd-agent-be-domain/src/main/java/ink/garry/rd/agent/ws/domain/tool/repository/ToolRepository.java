package ink.garry.rd.agent.ws.domain.tool.repository;

import ink.garry.rd.agent.ws.domain.tool.Tool;

/**
 * 工具聚合仓储接口。
 * <p>
 * 固定三方法契约（save / findByNum / deleteByNum）；其它读取（分页 / 详情 / 唯一性预检 /
 * 已发布挂载清单 / 复用数 / 引用检查等）一律走应用层 QueryService 经 Mapper 只读查询，
 * 不在本接口扩张。
 */
public interface ToolRepository {

    /**
     * 持久化 Tool 聚合（upsert 语义，不区分新增 / 更新）。
     *
     * @param aggregate 待保存的 Tool 聚合
     */
    void save(Tool aggregate);

    /**
     * 按业务编号加载 Tool 聚合。
     *
     * @param num 工具业务编号
     * @return Tool 聚合；不存在时返回 null
     */
    Tool findByNum(String num);

    /**
     * 按业务编号删除 Tool。
     * <p>
     * infra 实现：草稿态物理删除（DELETE），已发布 / 已废弃软删除（UPDATE deleted=1）；
     * 具体删除语义由聚合的 {@link Tool#delete(String)} 前置状态校验保证（仅草稿可进入物理删路径）。
     *
     * @param num 工具业务编号
     */
    void deleteByNum(String num);
}
