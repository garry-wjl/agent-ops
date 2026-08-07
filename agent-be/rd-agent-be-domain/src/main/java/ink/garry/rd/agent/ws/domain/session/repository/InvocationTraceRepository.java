package ink.garry.rd.agent.ws.domain.session.repository;

import ink.garry.rd.agent.ws.domain.session.InvocationTrace;

/**
 * InvocationTrace 聚合仓储：仅承担命令侧持久化与按编号读取。
 */
public interface InvocationTraceRepository {
    /**
     * 新增或更新调用记录。
     *
     * @param entity 待保存的实体（已通过 domainValidate）
     */
    void save(InvocationTrace entity);

    /**
     * 按业务编号加载调用记录。
     *
     * @param num 调用记录业务编号
     * @return 实体；不存在时返回 null
     */
    InvocationTrace findByNum(String num);

    /**
     * 按业务编号删除（软删/物理删由实现决定）。
     *
     * @param num 调用记录业务编号
     */
    void deleteByNum(String num);
}
