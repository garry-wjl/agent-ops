package ink.garry.rd.agent.ws.client.agent.dto;

import lombok.Data;

/**
 * Agent 对外调用秘钥内部认证 DTO（供 ApiKeyAuthenticationFilter 使用）。
 * <p>
 * authenticate(rawKey) 命中有效秘钥后返回；仅含路由 / 鉴权所需的非敏感标识，
 * 绝不携带 keyHash / keyCipher / 明文。
 */
@Data
public class AgentApiKeyDTO {

    /** 秘钥业务编号（前缀 AK） */
    private String num;

    /** 归属 Agent 业务编号（用于校验 body.agentNum 一致性） */
    private String agentNum;

    /** 归属工作空间业务编号（过滤器注入到 request 供后续用例隔离） */
    private String workspaceNum;
}
