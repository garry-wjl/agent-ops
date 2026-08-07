package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.client.agent.AgentApiKeyPlainVO;
import ink.garry.rd.agent.ws.client.agent.AgentApiKeyVO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentApiKeyDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentApiKeyFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentApiKeyGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 对外调用秘钥读侧应用服务：列表（掩码）/ 单条解密（小眼睛）/ 内部认证查。
 * <p>
 * 读用例：列表与认证查走 domain {@link AgentApiKeyGateway} 只读查询并转 client VO/DTO；
 * reveal 通过 {@link AgentApiKeyFactory} 加载后解密 cipher 回显明文并记审计。
 * 列表 / 认证查<b>绝不返回密文 / 明文</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentApiKeyQueryService {

    /** 掩码后缀，列表展示 keyPrefix + 该后缀 */
    private static final String MASK_SUFFIX = "****";

    private final AgentApiKeyGateway agentApiKeyGateway;
    private final AgentApiKeyFactory agentApiKeyFactory;
    private final SecretCipher secretCipher;

    /**
     * 列出某 Agent 下有效秘钥（仅掩码，不含密文 / 明文）。
     *
     * @param agentNum   Agent 业务编号
     * @param operatorId 操作人 userId（鉴权由 adapter 校验，这里仅承接）
     * @return 秘钥列表 VO（掩码）
     */
    public List<AgentApiKeyVO> listByAgent(String agentNum, String operatorId) {
        return agentApiKeyGateway.listByAgent(agentNum).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 单条解密（小眼睛）：校验归属 → 解密 key_cipher 返回明文 + 记审计。
     * <p>
     * 受登录态 + workspace 校验保护（由 adapter 把关）；本方法只负责归属一致性 + 解密 + 审计。
     *
     * @param agentNum   Agent 业务编号
     * @param keyNum     秘钥业务编号
     * @param operatorId 操作人 userId（审计记录用）
     * @return 含明文的 VO
     */
    public AgentApiKeyPlainVO reveal(String agentNum, String keyNum, String operatorId) {
        // 归属一致性校验
        if (!agentApiKeyGateway.existsByNumAndAgent(keyNum, agentNum)) {
            throw new BusinessException(BizCode.API_KEY_AGENT_MISMATCH.getCode(),
                    "秘钥与该 Agent 不匹配 agentNum=" + agentNum + " keyNum=" + keyNum);
        }
        AgentApiKey key = agentApiKeyFactory.createByNum(keyNum);
        if (key == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "秘钥不存在 num=" + keyNum);
        }
        String plain = secretCipher.decrypt(key.getKeyCipher());
        // 审计：谁在何时查看了哪把秘钥的明文（敏感操作留痕）
        log.info("[AUDIT][AgentApiKey.reveal] operator={} 查看了秘钥 keyNum={} agentNum={} 的明文",
                operatorId, keyNum, agentNum);

        AgentApiKeyPlainVO vo = new AgentApiKeyPlainVO();
        vo.setNum(key.getNum());
        vo.setKey(plain);
        return vo;
    }

    /**
     * 内部认证查（供 ApiKeyAuthenticationFilter）：SHA-256(rawKey) → findByKeyHash 命中有效秘钥。
     *
     * @param rawKey 调用方提交的明文密钥（Bearer ak-...）
     * @return 命中返回非敏感认证 DTO；未命中返回 null
     */
    public AgentApiKeyDTO authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String hash = secretCipher.sha256(rawKey);
        AgentApiKey key = agentApiKeyGateway.findByKeyHash(hash);
        if (key == null) {
            return null;
        }
        AgentApiKeyDTO dto = new AgentApiKeyDTO();
        dto.setNum(key.getNum());
        dto.setAgentNum(key.getAgentNum());
        dto.setWorkspaceNum(key.getWorkspaceNum());
        return dto;
    }

    /** 领域秘钥 → 列表 VO（仅掩码）。 */
    private AgentApiKeyVO toVO(AgentApiKey k) {
        AgentApiKeyVO vo = new AgentApiKeyVO();
        vo.setNum(k.getNum());
        vo.setAgentNum(k.getAgentNum());
        vo.setRemark(k.getRemark());
        vo.setKeyMasked(k.getKeyPrefix() == null ? null : k.getKeyPrefix() + MASK_SUFFIX);
        vo.setCreateNo(k.getCreateNo());
        vo.setCreateTime(k.getCreateTime());
        vo.setLastUsedAt(k.getLastUsedAt());
        return vo;
    }
}
