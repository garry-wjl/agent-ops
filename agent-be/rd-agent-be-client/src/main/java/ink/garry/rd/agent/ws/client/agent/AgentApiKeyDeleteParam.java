package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除 Agent 对外调用秘钥请求参数。
 * <p>
 * 服务端校验 num 对应秘钥归属 agentNum 一致后逻辑删除，删除即认证失效。
 */
@Data
public class AgentApiKeyDeleteParam {

    /** 归属 Agent 业务编号（前缀 AGT），必填 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 待删除秘钥业务编号（前缀 AK），必填 */
    @NotBlank(message = "num 不能为空")
    private String num;
}
