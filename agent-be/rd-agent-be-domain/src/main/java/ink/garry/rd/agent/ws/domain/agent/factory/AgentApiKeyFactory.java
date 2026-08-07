package ink.garry.rd.agent.ws.domain.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;

/**
 * AgentApiKey 工厂接口（实现位于 infra）。
 * <p>
 * 负责装配秘钥实体所需的 Repository / Gateway，并在创建阶段完成秘钥明文生成与加密三件套计算
 * （keyHash / keyCipher / keyPrefix）。明文仅作为返回值在内存回显一次，绝不持久化。
 */
public interface AgentApiKeyFactory {

    /**
     * 创建新秘钥。
     * <p>
     * 实现内：生成明文（{@code ak-} + 32 字节 base62 随机，SecureRandom）→ 计算
     * keyHash(SHA-256) / keyCipher(SecretCipher) / keyPrefix(掩码) → 装配依赖。
     * 返回的实体上明文不落字段，调用方需在 save 前从生成结果中取明文一次性回显。
     *
     * @param agentNum     关联的 Agent 业务编号
     * @param workspaceNum 归属工作空间业务编号（冗余自所属 Agent）
     * @param remark       用户备注，长度 ≤ 100，可空
     * @param operatorId   操作人 userId
     * @return 已装配依赖、未持久化的 AgentApiKey 实例（含 hash/cipher/prefix，无明文字段）
     */
    AgentApiKey create(String agentNum, String workspaceNum, String remark, String operatorId);

    /**
     * 通过 num 从仓储加载并重建秘钥（委托 {@code AgentApiKeyRepository.findByNum}）。
     *
     * @param num 秘钥业务编号
     * @return 已装配依赖的 AgentApiKey；不存在返回 null
     */
    AgentApiKey createByNum(String num);
}
