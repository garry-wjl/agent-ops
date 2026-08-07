package ink.garry.rd.agent.ws.domain.session.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 助手消息按到达顺序的段（与 FE AssistantSegment 同构,见 rd-agent-fe/src/types/session.ts）。
 * <p>
 * 段类型由 {@link #kind} 判别,取值 {@code "thinking"} / {@code "text"} / {@code "tool_use"}。
 * 同一对象同时持有所有可能字段,非当前 kind 用到的字段为 {@code null} —— FastJSON2 序列化时
 * 默认跳过 null,落库 JSON 自然与 FE union type 同形。
 * <p>
 * 设计权衡:不采用 Jackson 多态 / sealed interface,因为本项目 Entity 序列化统一走 FastJSON2,
 * 且 FastJSON2 多态语义与 FE 不一致(默认 typeKey="@type")。POJO + kind 字段最简且无歧义。
 * <p>
 * BE 累积器 SegmentAccumulator 复用 FE useInvokeStream 的 appendBlock / applyToolResult 等价逻辑。
 *
 * @see ink.garry.rd.agent.ws.application.debugconsole.SegmentAccumulator
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantSegment {
    /** 段类型判别字段:{@code "thinking"} / {@code "text"} / {@code "tool_use"} */
    private String kind;

    /** thinking / text 段的文本(连续同类 chunk 已合并)。tool_use 段为 null。 */
    private String text;

    /** tool_use 段:工具调用 ID,与 ToolResultBlock.id 匹配。其它段为 null。 */
    private String toolCallId;

    /** tool_use 段:工具名(LLM 给出的真名,非 fragment 占位)。 */
    private String toolName;

    /** tool_use 段:参数 JSON 字符串(供 FE 兼容显示;BE 端通常直接用 input)。 */
    private String argsBuffer;

    /** tool_use 段:结构化参数对象。 */
    private Map<String, Object> input;

    /** tool_use 段:执行状态,{@code "pending"} / {@code "success"} / {@code "error"}。 */
    private String status;

    /** tool_use 段:工具返回的扁平化输出(纯 text 块拼字符串,否则原 ContentBlock 数组)。 */
    private Object output;

    /** tool_use 段:执行耗时(ms),tool_result 回填时计算。 */
    private Integer latencyMs;

    /** tool_use 段:开始时间(ISO 8601),用于排序与展示。 */
    private String startedAt;

    /** tool_use 段:执行失败时的错误描述,成功为 null。 */
    private String error;

    /** 标记此段 output 是否被字节上限截断;true 时 FE 展示"已截断,详见 trace ID"。 */
    private Boolean truncated;
}
