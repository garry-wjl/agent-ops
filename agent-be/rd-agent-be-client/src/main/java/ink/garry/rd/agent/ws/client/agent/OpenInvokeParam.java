package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 对外调用 Agent（open）请求参数（秘钥 Bearer 认证）。
 * <p>
 * {@code agentNum} 同时由秘钥隐含（key→agentNum），过滤器校验其与 key.agentNum 一致；
 * {@code operatorId} 调用方可选传，为空记 {@code system}。
 */
@Data
public class OpenInvokeParam {

    /** 目标 Agent 业务编号，必填且须与认证秘钥归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 用户输入文本，必填 */
    @NotBlank(message = "input 不能为空")
    private String input;

    /** 输入类型，默认 text（预留多模态扩展） */
    private String inputType;

    /** 会话业务编号；为空表示新会话由下游创建 */
    private String sessionNum;

    /** 调用方操作人标识，可空（为空记 system） */
    private String operatorId;

    /**
     * 调用上下文（可空）：扁平键值，用于进入 Agent 前替换系统提示词占位符 {@code {{key}}}，
     * 并浅合并写入会话默认上下文。
     */
    private Map<String, Object> context;
}
