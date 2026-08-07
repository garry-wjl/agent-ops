package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Agent 对外调用秘钥请求参数。
 * <p>
 * 系统生成 key 明文（{@code ak-} + 随机），仅本次创建响应回显一次；服务端绝不持久化明文。
 */
@Data
public class AgentApiKeyCreateParam {

    /** 归属 Agent 业务编号（前缀 AGT），必填 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 用户备注，便于区分多把秘钥用途，必填且 ≤ 100 字符 */
    @NotBlank(message = "remark 不能为空")
    @Size(max = 100, message = "remark 长度不能超过 100 字符")
    private String remark;
}
