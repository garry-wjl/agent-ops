package ink.garry.rd.agent.ws.infra.agent.factory;

import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentApiKeyFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentApiKeyGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentApiKeyRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AgentApiKeyFactory 实现：创建阶段生成秘钥明文并计算加密三件套，装配 Repository / Gateway / Publisher。
 * <p>
 * 与既有 {@code AgentFactoryImpl} / {@code AgentVersionFactoryImpl} 同构（本项目约定 FactoryImpl
 * 承担 {@link DomainEventPublisher} 注入，application 不再写 wire helper）。
 * <p>
 * <b>明文回显</b>：本工厂不在实体上保留明文字段；创建当次的明文由 application 通过
 * {@link SecretCipher#decrypt(String)} 解密 {@code keyCipher} 获得后一次性回显（cipher 可逆）。
 */
@Component
@RequiredArgsConstructor
public class AgentApiKeyFactoryImpl implements AgentApiKeyFactory {

    private final AgentApiKeyRepository agentApiKeyRepository;
    private final AgentApiKeyGateway agentApiKeyGateway;
    private final DomainEventPublisher domainEventPublisher;
    private final SecretCipher secretCipher;

    @Override
    public AgentApiKey create(String agentNum, String workspaceNum, String remark, String operatorId) {
        // 生成明文（ak- + 32 字节 base62 随机，SecureRandom），仅本方法内存在
        String rawKey = secretCipher.randomRawKey();

        AgentApiKey key = new AgentApiKey();
        key.setAgentNum(agentNum);
        key.setWorkspaceNum(workspaceNum);
        key.setRemark(remark);
        // 加密三件套：hash 认证 / cipher 可逆解密 / prefix 掩码；绝不落明文
        key.setKeyHash(secretCipher.sha256(rawKey));
        key.setKeyCipher(secretCipher.encrypt(rawKey));
        key.setKeyPrefix(secretCipher.maskPrefix(rawKey));
        return wire(key);
    }

    @Override
    public AgentApiKey createByNum(String num) {
        return wire(agentApiKeyRepository.findByNum(num));
    }

    /**
     * 装配领域实体的基础设施依赖。
     *
     * @param key 待装配的秘钥实体；为 null 时直接返回 null
     * @return 已装配 Repository / Gateway / Publisher 的实体
     */
    private AgentApiKey wire(AgentApiKey key) {
        if (key == null) {
            return null;
        }
        key.setAgentApiKeyRepository(agentApiKeyRepository);
        key.setAgentApiKeyGateway(agentApiKeyGateway);
        key.setDomainEventPublisher(domainEventPublisher);
        return key;
    }
}
