package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 对外 A2UI（v0.9.1）流式调用请求参数。
 * <p>
 * 鉴权与既有 Open API 一致（{@code Authorization: Bearer ak-...}）；
 * 本接口与 {@code /command/invoke} 并存，不替换原 AgentScope Event SSE。
 */
@Data
public class OpenA2uiInvokeParam {

    /** 目标 Agent 业务编号，必填且须与认证秘钥归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 用户输入文本，必填 */
    @NotBlank(message = "input 不能为空")
    private String input;

    /** 会话业务编号；为空表示新会话由下游创建 */
    private String sessionNum;

    /** 调用方操作人标识，可空（为空记 system） */
    private String operatorId;

    /**
     * A2UI surfaceId；可空，默认 {@code main}。
     */
    private String surfaceId;

    /**
     * 组件目录 URL；可空，默认 basic catalog。
     */
    private String catalogId;

    /**
     * 是否在 createSurface 上声明 {@code sendDataModel=true}（默认 true），
     * 以便客户端在 action 回传时附带完整 data model。
     */
    private Boolean sendDataModel;

    /**
     * 调用上下文（可空）：进入 Agent 前替换系统提示词占位符，并浅合并写入会话。
     */
    private Map<String, Object> context;
}
