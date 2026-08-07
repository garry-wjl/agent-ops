package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Agent 对外调用秘钥明文 VO（小眼睛 reveal 单条解密返回）。
 * <p>
 * 仅在登录态 + workspace 校验 + 审计记录通过后返回，承载解密后的完整明文；不用于列表场景。
 */
@Data
public class AgentApiKeyPlainVO {

    /** 秘钥业务编号（前缀 AK） */
    private String num;

    /** 解密后的完整明文密钥（ak-...） */
    private String key;
}
