package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 对外创建会话（open）请求参数（秘钥 Bearer 认证）。
 * <p>
 * 委托既有 SessionCommandService.createSession；{@code operatorId} 可空（为空记 {@code system}）。
 */
@Data
public class OpenSessionCreateParam {

    /** 目标 Agent 业务编号，必填且须与认证秘钥归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** Skill 提示（可空，用于会话初始挂载提示） */
    private String skillHint;

    /** 会话标题，可空（系统兜底） */
    private String title;

    /** 调用方操作人标识，可空（为空记 system） */
    private String operatorId;

    /** 会话默认调用上下文（可空），落库供后续 invoke 变量替换继承 */
    private Map<String, Object> context;
}
