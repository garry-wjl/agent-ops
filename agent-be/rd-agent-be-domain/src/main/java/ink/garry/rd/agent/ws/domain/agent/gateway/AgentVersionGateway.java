package ink.garry.rd.agent.ws.domain.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;

import java.util.List;

/**
 * AgentVersion 聚合网关（生成业务编码 + 仓储 3 方法之外的读能力，实现位于 infra）。
 * <p>
 * 按"一聚合一个 Gateway"约定：版本编码生成、按 current_flag / versionNum / status 查询、
 * 版本历史列举与 currentFlag 翻转统一收敛于此。
 */
public interface AgentVersionGateway {

    /**
     * 生成 AgentVersion 业务编号（前缀 AVN）；v3.0 起 DRAFT 行也用此方法生成 num。
     *
     * @return 形如 AVN20260511XXXXXX
     */
    String generateAgentVersionNum();

    /**
     * 查找当前在线版本（current_flag=1）；无返回 null。
     * 用于运行时快照拉取与对外暴露当前版本信息。
     *
     * @param agentNum Agent 业务编号
     * @return 当前在线版本；无则为 null
     */
    AgentVersion findCurrent(String agentNum);

    /**
     * 按 agentNum + versionNum 查找版本。
     * 用于回滚 / 历史详情页加载。
     *
     * @param agentNum   Agent 业务编号
     * @param versionNum 版本号 vX.Y.Z
     * @return 命中版本；无则为 null
     */
    AgentVersion findByAgentNumAndVersionNum(String agentNum, String versionNum);

    /**
     * v3.0：按 agentNum + status 查找版本（典型用例：找草稿）。
     * <p>
     * 同 agentNum 下：DRAFT 至多 1 行，PUBLISHED 至多 1 行；ARCHIVED 可有多行，
     * 此方法仅返回首条（实现按 update_time desc 排序），如需全部走 {@link #listByAgentNum}。
     *
     * @param agentNum Agent 业务编号
     * @param status   目标状态
     * @return 首条命中版本；无则为 null
     */
    AgentVersion findByAgentNumAndStatus(String agentNum, AgentVersionStatus status);

    /**
     * 列出某 Agent 的版本历史（按 published_at 倒序），limit 默认 50。
     *
     * @param agentNum Agent 业务编号
     * @param limit    最大返回条数
     * @return 版本历史列表（最新在前）
     */
    List<AgentVersion> listByAgentNum(String agentNum, int limit);

    /**
     * 翻转 current 标记：将 oldVersionId 置 0，newVersionId 置 1（需事务保护）。
     * 由 application 层在发布事务内调用，保证同一 Agent 永远只有一个 current 版本。
     *
     * @param oldVersionId 旧在线版本主键
     * @param newVersionId 新在线版本主键
     */
    void switchCurrent(Long oldVersionId, Long newVersionId);
}
