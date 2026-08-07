package ink.garry.rd.agent.ws.domain.skillcheck.repository;

import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;

/**
 * SkillCheckRecord 聚合仓储接口。
 * <p>
 * 仅暴露 save / findByNum / deleteByNum 三个命令路径方法；列表 / 分页等读取走 gateway 或 application 直查 Mapper。
 */
public interface SkillCheckRecordRepository {

    /**
     * 持久化检测记录聚合（upsert 语义；常规仅 INSERT）。
     *
     * @param aggregate 待保存的检测记录聚合
     */
    void save(SkillCheckRecord aggregate);

    /**
     * 按业务编号加载检测记录聚合。
     *
     * @param num 检测记录业务编号
     * @return 检测记录聚合；不存在时返回 null
     */
    SkillCheckRecord findByNum(String num);

    /**
     * 按业务编号软删除检测记录。
     *
     * @param num 检测记录业务编号
     */
    void deleteByNum(String num);
}
