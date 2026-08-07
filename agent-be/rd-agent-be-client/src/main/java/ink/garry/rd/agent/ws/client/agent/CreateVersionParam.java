package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * v3.0 [+ 创建版本] 入参：拷贝当前在线版本 snapshot 生成新草稿。
 */
@Data
public class CreateVersionParam {
    /** Agent 业务编号 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;
}
