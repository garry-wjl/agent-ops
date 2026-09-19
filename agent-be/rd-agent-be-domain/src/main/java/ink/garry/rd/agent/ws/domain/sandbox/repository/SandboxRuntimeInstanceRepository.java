package ink.garry.rd.agent.ws.domain.sandbox.repository;

import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxRuntimeStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 沙箱运行时实例仓储。
 */
public interface SandboxRuntimeInstanceRepository {

    /**
     * 按业务编号查询。
     *
     * @param num 实例 num
     * @return 可选记录
     */
    Optional<SandboxRuntimeInstanceRecord> findByNum(String num);

    /**
     * 按会话查找已绑定实例。
     *
     * @param sessionNum 会话编号
     * @return 可选记录
     */
    Optional<SandboxRuntimeInstanceRecord> findBoundBySessionNum(String sessionNum);

    /**
     * 列出某资产下指定状态的实例。
     *
     * @param sandboxNum 资产编号
     * @param status     状态
     * @return 列表
     */
    List<SandboxRuntimeInstanceRecord> listBySandboxAndStatus(String sandboxNum, SandboxRuntimeStatus status);

    /**
     * 统计某资产活实例数（未删）。
     *
     * @param sandboxNum 资产编号
     * @return 数量
     */
    long countAlive(String sandboxNum);

    /**
     * 统计某资产 IDLE 数。
     *
     * @param sandboxNum 资产编号
     * @return 数量
     */
    long countIdle(String sandboxNum);

    /**
     * 列出某资产全部未删实例。
     *
     * @param sandboxNum 资产编号
     * @return 列表
     */
    List<SandboxRuntimeInstanceRecord> listBySandbox(String sandboxNum);

    /**
     * 列出超时未活跃的 BOUND 实例。
     *
     * @param before 早于该时间的 lastActiveAt
     * @return 列表
     */
    List<SandboxRuntimeInstanceRecord> listBoundInactiveBefore(LocalDateTime before);

    /**
     * 插入。
     *
     * @param record 记录
     */
    void insert(SandboxRuntimeInstanceRecord record);

    /**
     * 更新。
     *
     * @param record 记录
     */
    void update(SandboxRuntimeInstanceRecord record);

    /**
     * 软删。
     *
     * @param num 业务编号
     */
    void softDelete(String num);

    /**
     * 运行时实例持久化记录（贫血，由应用层编排状态）。
     *
     * @param num                     业务编号
     * @param sandboxNum              资产编号
     * @param workspaceNum            工作空间
     * @param opensandboxInstanceId   远程 id
     * @param status                  状态
     * @param sessionNum              会话
     * @param lastActiveAt            最近活跃
     * @param idleSince               进入空闲时间
     * @param createNo                创建人
     * @param updateNo                更新人
     */
    record SandboxRuntimeInstanceRecord(
            String num,
            String sandboxNum,
            String workspaceNum,
            String opensandboxInstanceId,
            SandboxRuntimeStatus status,
            String sessionNum,
            LocalDateTime lastActiveAt,
            LocalDateTime idleSince,
            String createNo,
            String updateNo) {
    }
}
