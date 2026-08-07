package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollectionUtil;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AgentRunnerService#transformEvent} 单元测试。
 * <p>
 * 核心回归：MiniMax 将推理内容以 {@code <mm:think>...</mm:think>} 包裹放进 {@code content} 字段，
 * AgentScope 2.0 的 formatter 将其误转为 TextBlock。transformEvent 在 REASONING 事件中解析该标签，
 * 将标签内文本拆分为 ThinkingBlock，使得 SegmentAccumulator 和前端的 thinking 识别逻辑正确触发。
 */
class AgentRunnerServiceTransformEventTest {

    private Event reasoningEvent(Msg msg) {
        return new Event(EventType.REASONING, msg, false);
    }

    private Msg msgWithBlocks(List<ContentBlock> blocks) {
        return Msg.builder()
                .id("msg-1")
                .name("test-agent")
                .role(io.agentscope.core.message.MsgRole.ASSISTANT)
                .content(blocks)
                .build();
    }

    private TextBlock text(String t) {
        return TextBlock.builder().text(t).build();
    }

    // ============= 标签解析 =============

    @Test
    void shouldConvertMmThinkTagToThinkingBlock() {
        // <mm:think>推理内容</mm:think>正文
        Msg msg = msgWithBlocks(List.of(
                text("<mm:think>The user is just saying hello</mm:think>你好！")
        ));
        AtomicBoolean flag = new AtomicBoolean(false);

        Event result = AgentRunnerService.transformEvent(new Event(EventType.REASONING, msg, true), flag);

        List<ContentBlock> blocks = result.getMessage().getContent();
        assertEquals(2, blocks.size());
        assertInstanceOf(ThinkingBlock.class, blocks.get(0));
        assertEquals("The user is just saying hello", ((ThinkingBlock) blocks.get(0)).getThinking());
        assertInstanceOf(TextBlock.class, blocks.get(1));
        assertEquals("你好！", ((TextBlock) blocks.get(1)).getText());
        assertFalse(flag.get());
    }

    @Test
    void shouldHandleThinkTagWithPrefixText() {
        // 前缀<mm:think>推理</mm:think>后缀
        Msg msg = msgWithBlocks(List.of(
                text("前缀<mm:think>推理过程</mm:think>正文回答")
        ));
        AtomicBoolean flag = new AtomicBoolean(false);

        Event result = AgentRunnerService.transformEvent(reasoningEvent(msg), flag);

        List<ContentBlock> blocks = result.getMessage().getContent();
        assertEquals(3, blocks.size());
        assertInstanceOf(TextBlock.class, blocks.get(0));
        assertEquals("前缀", ((TextBlock) blocks.get(0)).getText());
        assertInstanceOf(ThinkingBlock.class, blocks.get(1));
        assertEquals("推理过程", ((ThinkingBlock) blocks.get(1)).getThinking());
        assertInstanceOf(TextBlock.class, blocks.get(2));
        assertEquals("正文回答", ((TextBlock) blocks.get(2)).getText());
        assertFalse(flag.get());
    }

    @Test
    void shouldHandleThinkTagOnlyStartInOneEventThenCloseInNext() {
        // 第 1 帧：<mm:think>推理开始
        AtomicBoolean flag = new AtomicBoolean(false);
        Msg msg1 = msgWithBlocks(List.of(text("<mm:think>推理开始")));
        AgentRunnerService.transformEvent(reasoningEvent(msg1), flag);
        assertTrue(flag.get());

        // 第 2 帧：继续推理</mm:think>正文
        Msg msg2 = msgWithBlocks(List.of(text(" 继续推理</mm:think>正文回答")));
        Event result = AgentRunnerService.transformEvent(reasoningEvent(msg2), flag);

        List<ContentBlock> blocks = result.getMessage().getContent();
        assertEquals(2, blocks.size());
        assertInstanceOf(ThinkingBlock.class, blocks.get(0));
        assertEquals(" 继续推理", ((ThinkingBlock) blocks.get(0)).getThinking());
        assertInstanceOf(TextBlock.class, blocks.get(1));
        assertEquals("正文回答", ((TextBlock) blocks.get(1)).getText());
        assertFalse(flag.get());
    }

    @Test
    void shouldReturnEventUnchangedForNullMessageContent() {
        AtomicBoolean flag = new AtomicBoolean(false);

        // msg 为 null
        Event result1 = AgentRunnerService.transformEvent(reasoningEvent(null), flag);
        assertNotNull(result1);
        assertNull(result1.getMessage());

        // content 为 null
        Event result2 = AgentRunnerService.transformEvent(
                reasoningEvent(Msg.builder().id("x").name("t").role(io.agentscope.core.message.MsgRole.ASSISTANT).build()), flag);
        assertNotNull(result2);

        // 空的 content 列表
        Event result3 = AgentRunnerService.transformEvent(
                reasoningEvent(msgWithBlocks(List.of())), flag);
        assertNotNull(result3);
        assertTrue(CollectionUtil.isEmpty(result3.getMessage().getContent()));
    }

    // ============= 非 REASONING 事件透出 =============

    @Test
    void shouldPassthroughNonReasoningEvent() {
        Event toolResult = new Event(EventType.TOOL_RESULT, msgWithBlocks(List.of(text("data"))), true);
        AtomicBoolean flag = new AtomicBoolean(false);

        Event result = AgentRunnerService.transformEvent(toolResult, flag);

        assertSame(toolResult, result);
    }

    // ============= 已有的标准 ThinkingBlock 不受影响 =============

    @Test
    void shouldNotModifyExistingThinkingBlock() {
        ThinkingBlock existing = ThinkingBlock.builder().thinking("标准深度思考").build();
        TextBlock normal = text("正常文本");
        Msg msg = msgWithBlocks(List.of(existing, normal));
        AtomicBoolean flag = new AtomicBoolean(false);

        Event result = AgentRunnerService.transformEvent(reasoningEvent(msg), flag);

        List<ContentBlock> blocks = result.getMessage().getContent();
        assertEquals(2, blocks.size());
        assertInstanceOf(ThinkingBlock.class, blocks.get(0));
        assertEquals("标准深度思考", ((ThinkingBlock) blocks.get(0)).getThinking());
        assertInstanceOf(TextBlock.class, blocks.get(1));
        assertEquals("正常文本", ((TextBlock) blocks.get(1)).getText());
        assertFalse(flag.get());
    }

    // ============= 纯文本无标签 =============

    @Test
    void shouldNotModifyPlainText() {
        Msg msg = msgWithBlocks(List.of(text("Hello"), text(" World")));
        AtomicBoolean flag = new AtomicBoolean(false);

        Event result = AgentRunnerService.transformEvent(reasoningEvent(msg), flag);

        List<ContentBlock> blocks = result.getMessage().getContent();
        assertEquals(2, blocks.size());
        assertInstanceOf(TextBlock.class, blocks.get(0));
        assertInstanceOf(TextBlock.class, blocks.get(1));
        assertFalse(flag.get());
    }
}