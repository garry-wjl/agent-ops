package ink.garry.rd.agent.ws.domain.skill.repository;

import ink.garry.rd.agent.ws.domain.skill.Skill;

/**
 * Skill 聚合仓储接口。
 * <p>
 * 仅暴露 save / findByNum / deleteByNum 三个命令路径方法，其它读取走 ReadGateway。
 */
public interface SkillRepository {
    /**
     * 持久化 Skill 聚合（upsert 语义）。
     *
     * @param aggregate 待保存的 Skill 聚合
     */
    void save(Skill aggregate);

    /**
     * 按业务编号加载 Skill 聚合。
     *
     * @param num Skill 业务编号
     * @return Skill 聚合；不存在时返回 null
     */
    Skill findByNum(String num);

    /**
     * 按业务编号软删除 Skill。
     *
     * @param num Skill 业务编号
     */
    void deleteByNum(String num);
}
