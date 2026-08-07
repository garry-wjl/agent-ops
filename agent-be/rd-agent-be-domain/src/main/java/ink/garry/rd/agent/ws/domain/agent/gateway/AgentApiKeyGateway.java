package ink.garry.rd.agent.ws.domain.agent.gateway;

import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;

import java.util.List;

/**
 * Agent 对外调用秘钥聚合网关（生成业务编码 + 仓储 3 方法之外的读能力，实现位于 infra）。
 * <p>
 * 按"一聚合一个 Gateway"约定：秘钥编码生成、列表 / 计数 / 认证查（按 hash）/ 归属校验统一收敛于此，
 * 避免污染 Repository 契约。
 */
public interface AgentApiKeyGateway {

    /**
     * 生成 AgentApiKey 业务编号（前缀 AK）；用于对外调用秘钥的业务唯一标识。
     *
     * @return 形如 AK20260604XXXXXX
     */
    String generateAgentApiKeyNum();

    /**
     * 列出某 Agent 下的全部有效秘钥（排除已删除），用于秘钥管理 Tab 列表展示。
     * <p>
     * 列表场景仅返回掩码，调用方不应回显密文 / 明文。
     *
     * @param agentNum Agent 业务编号
     * @return 秘钥实体列表，按创建时间倒序；无数据返回空列表
     */
    List<AgentApiKey> listByAgent(String agentNum);

    /**
     * 统计某 Agent 下有效秘钥数量，用于创建前的 ≤ 50 数量上限校验。
     *
     * @param agentNum Agent 业务编号
     * @return 有效秘钥数量
     */
    long countByAgent(String agentNum);

    /**
     * 按 keyHash 等值查询秘钥，用于对外调用认证（唯一索引等值查，P99 < 5ms）。
     *
     * @param keyHash SHA-256(明文) 哈希
     * @return 匹配的有效秘钥；不存在或已删除返回 null
     */
    AgentApiKey findByKeyHash(String keyHash);

    /**
     * 校验秘钥归属：num 对应的秘钥是否属于指定 agentNum，用于删除 / 查看时的归属一致性校验。
     *
     * @param num      秘钥业务编号
     * @param agentNum Agent 业务编号
     * @return true=该秘钥存在且归属匹配
     */
    boolean existsByNumAndAgent(String num, String agentNum);
}
