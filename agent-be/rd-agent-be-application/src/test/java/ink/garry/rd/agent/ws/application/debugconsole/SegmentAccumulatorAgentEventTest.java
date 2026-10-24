package ink.garry.rd.agent.ws.application.debugconsole;

import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SegmentAccumulator#acceptAgentEvent}：streamEvents 细粒度帧累积。
 */
class SegmentAccumulatorAgentEventTest {

    @Test
    void accumulatesTextAndThinkingDeltas() {
        SegmentAccumulator acc = new SegmentAccumulator();
        acc.acceptAgentEvent(new TextBlockDeltaEvent("r1", "b1", "hello"));
        acc.acceptAgentEvent(new TextBlockDeltaEvent("r1", "b1", " world"));
        acc.acceptAgentEvent(new ThinkingBlockDeltaEvent("r1", "t1", "think"));

        assertEquals(2, acc.getSegments().size());
        assertEquals("text", acc.getSegments().get(0).getKind());
        assertEquals("hello world", acc.getSegments().get(0).getText());
        assertEquals("thinking", acc.getSegments().get(1).getKind());
        assertEquals("think", acc.getSegments().get(1).getText());
    }

    @Test
    void accumulatesToolCallAndResult() {
        SegmentAccumulator acc = new SegmentAccumulator();
        acc.acceptAgentEvent(new ToolCallStartEvent("r1", "tc1", "shell"));
        acc.acceptAgentEvent(new ToolCallDeltaEvent("r1", "tc1", "shell", "{\"cmd\":\"ls\"}"));
        acc.acceptAgentEvent(new ToolResultTextDeltaEvent("r1", "tc1", "shell", "a.txt"));
        acc.acceptAgentEvent(new ToolResultEndEvent("r1", "tc1", "shell", null));

        assertEquals(1, acc.getSegments().size());
        AssistantSegment seg = acc.getSegments().get(0);
        assertEquals("tool_use", seg.getKind());
        assertEquals("tc1", seg.getToolCallId());
        assertEquals("shell", seg.getToolName());
        assertEquals("success", seg.getStatus());
        assertEquals("a.txt", seg.getOutput());
        assertTrue(seg.getInput() != null && "ls".equals(seg.getInput().get("cmd")));
    }
}
