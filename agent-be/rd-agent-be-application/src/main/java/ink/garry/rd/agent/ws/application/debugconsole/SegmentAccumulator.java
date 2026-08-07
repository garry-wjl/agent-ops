package ink.garry.rd.agent.ws.application.debugconsole;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepChain;
import ink.garry.rd.agent.ws.domain.session.valueobject.StepNode;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试台 invoke 流式期间累积 AssistantSegment 与 AgentScope 原始 ContentBlock 列表的工具类。
 * <p>
 * 订阅策略:只看 PostReasoning / PostActing 的 isLast=true 帧 —— AgentScope 已在这两帧给出
 * 累积视图,无需复刻 FE 的 chunk delta 累积或 tool_use fragment 拼接逻辑。
 * <p>
 * 与 FE useInvokeStream 的 appendBlock / applyToolResult 行为等价(同类相邻合并、按 id 回填),
 * 这样保证持久化的 segments 与本轮流式 FE 实际展示的 segments 1:1 同构。
 * <p>
 * 线程模型:每次 invoke 创建一个实例,在 Reactor 链 doOnNext 内调用 {@link #accept(Event)}。
 * AgentScope Flux 默认串行下发同 sink 的事件,故此累积器**非线程安全**,不要跨流复用。
 *
 * @see ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService
 */
public class SegmentAccumulator {

    /** 单段 tool_use.output 字节上限,超出截断 + 标记 truncated=true */
    private static final int TOOL_OUTPUT_BYTE_LIMIT = 64 * 1024;

    @Getter
    private final List<AssistantSegment> segments = new ArrayList<>();

    /** AgentScope Msg.content 原始 block 列表(去重后,按时序),供 trace 导出 */
    @Getter
    private final List<ContentBlock> contentBlocks = new ArrayList<>();

    /** toolCallId -> 开始时间戳(ms),用于 tool_result 到达时算 latency */
    private final Map<String, Long> toolStartedAt = new HashMap<>();

    /**
     * 喂入一个事件。
     * <p>chunk 帧(isLast=false)仅用于记录 tool_use 首次出现时间(便于后续算 latency);
     * 只有 isLast=true 的 REASONING / TOOL_RESULT 帧会写入累积器。
     */
    public void accept(Event event) {
        if (event == null) return;
        if (!event.isLast()) {
            if (event.getType() == EventType.REASONING && event.getMessage() != null) {
                for (ContentBlock b : event.getMessage().getContent()) {
                    if (b instanceof ToolUseBlock t) {
                        toolStartedAt.putIfAbsent(t.getId(), System.currentTimeMillis());
                    }
                }
            }
            return;
        }
        if (event.getType() == EventType.REASONING) {
            handleReasoning(event.getMessage());
        } else if (event.getType() == EventType.TOOL_RESULT) {
            handleToolResult(event.getMessage());
        }
        // AGENT_RESULT / SUMMARY / HINT:不再追加 —— AGENT_RESULT 等价于 REASONING 末尾 text 总和,
        // 与 FE useInvokeStream 协议一致(在 case 分支里跳过)
    }

    /** 从累积的 tool_use 段派生 StepChain(保留旧字段持久化,FE 老降级路径可用) */
    public StepChain toStepChain() {
        List<StepNode> nodes = new ArrayList<>();
        for (AssistantSegment seg : segments) {
            if (!"tool_use".equals(seg.getKind())) continue;
            nodes.add(StepNode.builder()
                    .stepId(seg.getToolCallId())
                    .skillName(seg.getToolName())
                    .input(seg.getInput())
                    .output(seg.getOutput())
                    .status(seg.getStatus())
                    .latencyMs(seg.getLatencyMs())
                    .error(seg.getError())
                    .build());
        }
        return StepChain.builder().steps(nodes).build();
    }

    /** 把累积的 ContentBlock 列表序列化为 JSON 字符串,用于落 message.content_blocks_json */
    public String toContentBlocksJson() {
        return contentBlocks.isEmpty() ? null : JSON.toJSONString(contentBlocks);
    }

    /** 从所有 text 段汇总文本,用于落 message.content(旧字段,兼容) */
    public String toContentText() {
        StringBuilder sb = new StringBuilder();
        for (AssistantSegment seg : segments) {
            if ("text".equals(seg.getKind()) && seg.getText() != null) {
                sb.append(seg.getText());
            }
        }
        return sb.toString();
    }

    // ============== 内部:按帧处理 ==============

    private void handleReasoning(Msg msg) {
        if (msg == null || CollUtil.isEmpty(msg.getContent())) return;
        for (ContentBlock b : msg.getContent()) {
            contentBlocks.add(b);
            if (b instanceof ThinkingBlock tb) {
                appendThinking(tb.getThinking());
            } else if (b instanceof TextBlock tx) {
                appendText(tx.getText());
            } else if (b instanceof ToolUseBlock tu) {
                appendToolUse(tu);
            }
            // 其他类型(image/audio/video):本期忽略
        }
    }

    private void handleToolResult(Msg msg) {
        if (msg == null || CollUtil.isEmpty(msg.getContent())) return;
        for (ContentBlock b : msg.getContent()) {
            contentBlocks.add(b);
            if (b instanceof ToolResultBlock tr) {
                applyToolResult(tr);
            }
        }
    }

    /** 同类相邻合并:上一段是 thinking 则追加,否则新建段 */
    void appendThinking(String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (!segments.isEmpty()) {
            AssistantSegment last = segments.get(segments.size() - 1);
            if ("thinking".equals(last.getKind())) {
                last.setText((last.getText() == null ? "" : last.getText()) + delta);
                return;
            }
        }
        segments.add(AssistantSegment.builder().kind("thinking").text(delta).build());
    }

    /** 同类相邻合并:上一段是 text 则追加,否则新建段 */
    void appendText(String delta) {
        if (delta == null || delta.isEmpty()) return;
        if (!segments.isEmpty()) {
            AssistantSegment last = segments.get(segments.size() - 1);
            if ("text".equals(last.getKind())) {
                last.setText((last.getText() == null ? "" : last.getText()) + delta);
                return;
            }
        }
        segments.add(AssistantSegment.builder().kind("text").text(delta).build());
    }

    /**
     * PostReasoning 的 isLast=true 帧里 tool_use 已是完整(name 真实、input 完整对象),
     * 不需要 fragment 拼接。按 toolCallId 去重,已有则补齐字段。
     */
    void appendToolUse(ToolUseBlock block) {
        for (AssistantSegment s : segments) {
            if ("tool_use".equals(s.getKind()) && block.getId().equals(s.getToolCallId())) {
                if (s.getInput() == null && block.getInput() != null) s.setInput(block.getInput());
                if (s.getToolName() == null || s.getToolName().isEmpty()) s.setToolName(block.getName());
                return;
            }
        }
        Map<String, Object> input = block.getInput();
        segments.add(AssistantSegment.builder()
                .kind("tool_use")
                .toolCallId(block.getId())
                .toolName(block.getName())
                .argsBuffer(input == null ? "" : JSON.toJSONString(input))
                .input(input)
                .status("pending")
                .startedAt(Instant.now().toString())
                .build());
    }

    /** 反向找最近一个匹配 id 的 tool_use 段,回填 output / status / latency */
    void applyToolResult(ToolResultBlock block) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            AssistantSegment s = segments.get(i);
            if (!"tool_use".equals(s.getKind()) || !block.getId().equals(s.getToolCallId())) continue;
            FlattenResult flat = flattenToolOutput(block.getOutput());
            s.setOutput(flat.value);
            s.setTruncated(flat.truncated ? Boolean.TRUE : null);
            s.setStatus("success");
            Long startedAt = toolStartedAt.remove(block.getId());
            if (startedAt != null) {
                s.setLatencyMs(Math.toIntExact(System.currentTimeMillis() - startedAt));
            }
            return;
        }
        // 未匹配(孤立 tool_result):静默忽略;调试台只展示用,无需独占一段
    }

    /**
     * 与 FE flattenToolOutput 同构:纯 text 块拼字符串,否则原 List 返回。
     * 同时做 byte 限制:超 {@value #TOOL_OUTPUT_BYTE_LIMIT} 字节截断 + 标记 truncated=true。
     */
    FlattenResult flattenToolOutput(List<ContentBlock> output) {
        if (CollUtil.isEmpty(output)) return new FlattenResult(null, false);
        StringBuilder sb = new StringBuilder();
        boolean allText = true;
        for (ContentBlock b : output) {
            if (b instanceof TextBlock t) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t.getText() == null ? "" : t.getText());
            } else {
                allText = false;
                break;
            }
        }
        if (allText) {
            String text = sb.toString();
            if (text.length() > TOOL_OUTPUT_BYTE_LIMIT) {
                return new FlattenResult(text.substring(0, TOOL_OUTPUT_BYTE_LIMIT), true);
            }
            return new FlattenResult(text, false);
        }
        return new FlattenResult(output, false);
    }

    /** flattenToolOutput 内部返回包装:同时携带 truncated 标记 */
    record FlattenResult(Object value, boolean truncated) {}
}
