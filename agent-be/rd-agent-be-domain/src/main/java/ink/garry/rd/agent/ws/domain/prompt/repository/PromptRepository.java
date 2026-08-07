package ink.garry.rd.agent.ws.domain.prompt.repository;

import ink.garry.rd.agent.ws.domain.prompt.Prompt;

/**
 * Prompt 聚合仓储接口。
 * <p>
 * 仅暴露 save / findByNum / deleteByNum 三个命令路径方法（固定三方法契约）；
 * 其它读取（列表 / 详情 / 唯一性预检）走应用层 QueryService + MyBatis Mapper，不在此扩张。
 */
public interface PromptRepository {

    /**
     * 持久化 Prompt 聚合（upsert 语义，不区分新增 / 更新）。
     *
     * @param aggregate 待保存的 Prompt 聚合
     */
    void save(Prompt aggregate);

    /**
     * 按业务编号加载 Prompt 聚合。
     *
     * @param num Prompt 业务编号
     * @return Prompt 聚合；不存在时返回 {@code null}
     */
    Prompt findByNum(String num);

    /**
     * 按业务编号软删除 Prompt。
     *
     * @param num Prompt 业务编号
     */
    void deleteByNum(String num);
}
