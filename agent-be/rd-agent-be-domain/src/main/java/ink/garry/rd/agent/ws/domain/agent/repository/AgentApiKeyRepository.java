package ink.garry.rd.agent.ws.domain.agent.repository;

import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;

/**
 * Agent 对外调用秘钥仓储接口（仅 3 方法）。
 * <p>
 * 列表 / 计数 / 按 hash 认证查 / 归属校验等读能力见 gateway/AgentApiKeyGateway，
 * 不污染本 Repository 契约。
 */
public interface AgentApiKeyRepository {

    /**
     * 持久化秘钥（不区分新增 / 更新）；仅落 keyHash / keyCipher / keyPrefix，绝不落明文。
     *
     * @param aggregate 待保存的秘钥实体
     */
    void save(AgentApiKey aggregate);

    /**
     * 按业务编号加载秘钥。
     *
     * @param num 秘钥业务编号（前缀 AK）
     * @return 秘钥实体；不存在返回 null
     */
    AgentApiKey findByNum(String num);

    /**
     * 按业务编号逻辑删除秘钥；删除后该 key 认证立即失效。
     *
     * @param num 秘钥业务编号
     */
    void deleteByNum(String num);
}
