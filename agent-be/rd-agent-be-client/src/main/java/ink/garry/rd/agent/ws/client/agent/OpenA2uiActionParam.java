package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 对外 A2UI（v0.9.1）客户端 action 回传请求。
 * <p>
 * 对应协议 client→server 的 {@code action} 消息；HTTP 绑定额外携带
 * {@code agentNum}/{@code sessionNum} 以及可选的 {@code clientDataModel}
 * （当 createSurface.sendDataModel=true 时由客户端附带）。
 */
@Data
public class OpenA2uiActionParam {

    /** 目标 Agent 业务编号，必填且须与认证秘钥归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 会话业务编号；建议携带以延续同一对话；可空则下游新建 */
    private String sessionNum;

    /** 调用方操作人标识，可空（为空记 system） */
    private String operatorId;

    /**
     * A2UI action 载荷（协议字段）；必填。
     */
    @NotNull(message = "action 不能为空")
    @Valid
    private A2uiActionPayload action;

    /**
     * 客户端 data model 快照（可空）。
     * <p>
     * 对应传输元数据中的 {@code a2uiClientDataModel.surfaces}；推荐结构为
     * {@code surfaceId → dataModelObject}，也可直接传当前 surface 的 model 对象。
     */
    private Map<String, Object> clientDataModel;

    /**
     * 额外调用上下文（可空），会与 action/context/clientDataModel 一并进入 Agent。
     */
    private Map<String, Object> context;

    /**
     * A2UI action 协议载荷。
     */
    @Data
    public static class A2uiActionPayload {

        /** 动作名，必填 */
        @NotBlank(message = "action.name 不能为空")
        private String name;

        /** 动作来源 surfaceId，必填 */
        @NotBlank(message = "action.surfaceId 不能为空")
        private String surfaceId;

        /** 触发组件 ID，必填 */
        @NotBlank(message = "action.sourceComponentId 不能为空")
        private String sourceComponentId;

        /** ISO-8601 时间戳，必填 */
        @NotBlank(message = "action.timestamp 不能为空")
        private String timestamp;

        /** 动作上下文对象，必填（可为空对象） */
        @NotNull(message = "action.context 不能为空")
        private Map<String, Object> context;
    }
}
