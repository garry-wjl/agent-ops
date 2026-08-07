package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话内单条消息 VO。
 * <p>
 * 包含角色（user/assistant/system）、输入类型、消息内容（string|object）、Agent 内部执行的步骤链
 * （见 {@link StepChainVO}）以及全链路 traceId。content 字段类型为 Object，按 inputType 解析。
 */
@Data
public class MessageVO {
    /** 消息业务编号 */
    private String num;
    /** 消息角色：user / assistant / system */
    private String role;
    /** 输入类型（如 text / json / multimodal），决定 content 的实际结构 */
    private String inputType;
    /** 消息内容；类型由 inputType 决定（string 或 object） */
    private Object content;
    /** Agent 内部的执行步骤链（仅 assistant 消息可能有；新协议下从 segments 派生） */
    private StepChainVO stepChain;
    /**
     * 助手消息按到达顺序的段列表(thinking / text / tool_use)。
     * <p>v3.x 新协议:FE 历史与本轮流式共用 AssistantSegmentList 渲染路径。
     * USER / TOOL 消息或旧消息(无 segments_json)为 null,FE 走 content + stepChain 降级。
     */
    private List<AssistantSegmentVO> segments;
    /** 全链路 traceId，用于排查与日志关联 */
    private String traceId;
    /** 消息创建时间 */
    private LocalDateTime createTime;
}
