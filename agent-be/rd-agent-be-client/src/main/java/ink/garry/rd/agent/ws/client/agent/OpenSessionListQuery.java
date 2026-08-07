package ink.garry.rd.agent.ws.client.agent;

import ink.garry.rd.agent.ws.client.common.PageParam;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对外会话列表（open）分页查询入参（秘钥 Bearer 认证）。
 * <p>
 * 继承通用分页 {@link PageParam}（pageNo/pageSize）；{@code operatorId} 可空（为空记 {@code system}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenSessionListQuery extends PageParam {

    /** 目标 Agent 业务编号，必填且须与认证秘钥归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 调用方操作人标识，可空（为空记 system） */
    private String operatorId;
}
