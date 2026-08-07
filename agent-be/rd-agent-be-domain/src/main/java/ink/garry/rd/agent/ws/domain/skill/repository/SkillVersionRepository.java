package ink.garry.rd.agent.ws.domain.skill.repository;

import ink.garry.rd.agent.ws.domain.skill.SkillVersion;

/**
 * SkillVersion 聚合仓储接口。
 * <p>
 * 仅暴露 save / findByNum / deleteByNum 三个命令路径方法，其它读取走 ReadGateway。
 */
public interface SkillVersionRepository {
    /**
     * 持久化版本聚合（upsert 语义）。
     *
     * @param aggregate 待保存的版本聚合
     */
    void save(SkillVersion aggregate);

    /**
     * 按业务编号加载版本聚合。
     *
     * @param num 版本业务编号
     * @return 版本聚合；不存在时返回 null
     */
    SkillVersion findByNum(String num);

    /**
     * 按业务编号软删除版本。
     *
     * @param num 版本业务编号
     */
    void deleteByNum(String num);
}
