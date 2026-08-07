package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.util.Map;

/**
 * 助手消息按到达顺序的段 VO,字段与 FE {@code AssistantSegment}(rd-agent-fe/src/types/session.ts)严格同构。
 * <p>
 * 段类型由 {@link #kind} 判别:{@code "thinking"} / {@code "text"} / {@code "tool_use"}。
 * 非当前 kind 用到的字段为 null,Jackson 输出时默认跳过 null,JSON 形态与 FE union type 同形。
 * <p>
 * 来源:由 {@code SessionQueryService} 从 {@code MessageEntity.segmentsJson} 反序列化得到。
 */
@Data
public class AssistantSegmentVO {
    /** 段类型:{@code "thinking"} / {@code "text"} / {@code "tool_use"} */
    private String kind;

    /** thinking / text 段:文本 */
    private String text;

    /** tool_use 段:工具调用 ID */
    private String toolCallId;

    /** tool_use 段:工具名 */
    private String toolName;

    /** tool_use 段:参数 JSON 字符串 */
    private String argsBuffer;

    /** tool_use 段:结构化参数 */
    private Map<String, Object> input;

    /** tool_use 段:状态 pending / success / error */
    private String status;

    /** tool_use 段:输出 */
    private Object output;

    /** tool_use 段:耗时 ms */
    private Integer latencyMs;

    /** tool_use 段:开始时间 ISO 8601 */
    private String startedAt;

    /** tool_use 段:错误信息 */
    private String error;

    /** output 是否被字节上限截断 */
    private Boolean truncated;
}
