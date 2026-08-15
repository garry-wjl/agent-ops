package ink.garry.rd.agent.ws.application.a2ui;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2UI v0.9.1 编码器单测：bootstrap 形态与文本增量映射。
 */
class A2uiV091EncoderTest {

    @Test
    void bootstrap_shouldEmitCreateSurfaceComponentsAndDataModel() {
        A2uiV091Encoder encoder = new A2uiV091Encoder("main", null, true);
        List<Map<String, Object>> msgs = encoder.bootstrap();
        assertEquals(3, msgs.size());
        assertEquals(A2uiProtocol.VERSION, msgs.get(0).get("version"));
        assertTrue(msgs.get(0).containsKey("createSurface"));
        assertTrue(msgs.get(1).containsKey("updateComponents"));
        assertTrue(msgs.get(2).containsKey("updateDataModel"));

        @SuppressWarnings("unchecked")
        Map<String, Object> create = (Map<String, Object>) msgs.get(0).get("createSurface");
        assertEquals("main", create.get("surfaceId"));
        assertEquals(A2uiProtocol.DEFAULT_CATALOG_ID, create.get("catalogId"));
        assertEquals(true, create.get("sendDataModel"));
    }

    @Test
    void encode_shouldStreamAssistantTextViaDataModel() {
        A2uiV091Encoder encoder = new A2uiV091Encoder("s1", null, true);
        encoder.bootstrap();

        Event chunk1 = new Event(EventType.REASONING, Msg.builder()
                .content(TextBlock.builder().text("你").build())
                .build(), false);
        Event chunk2 = new Event(EventType.REASONING, Msg.builder()
                .content(TextBlock.builder().text("好").build())
                .build(), false);
        Event last = new Event(EventType.REASONING, Msg.builder()
                .content(TextBlock.builder().text("你好").build())
                .build(), true);
        Event result = new Event(EventType.AGENT_RESULT, Msg.builder()
                .content(TextBlock.builder().text("你好").build())
                .usage(io.agentscope.core.model.ChatUsage.builder()
                        .inputTokens(12)
                        .outputTokens(3)
                        .cachedTokens(1)
                        .time(0.4)
                        .build())
                .build(), true);

        List<Map<String, Object>> m1 = encoder.encode(chunk1);
        List<Map<String, Object>> m2 = encoder.encode(chunk2);
        List<Map<String, Object>> m3 = encoder.encode(last);
        List<Map<String, Object>> m4 = encoder.encode(result);

        assertEquals(1, m1.size());
        assertEquals("你", dataModelValue(m1.get(0)));
        assertEquals("你好", dataModelValue(m2.get(0)));
        assertEquals("你好", dataModelValue(m3.get(0)));
        assertEquals(1, m4.size(), "AGENT_RESULT should emit token usage");
        assertEquals(A2uiProtocol.TOKEN_USAGE_PATH, dataModelPath(m4.get(0)));
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) dataModelValue(m4.get(0));
        assertEquals(12, usage.get("inputTokens"));
        assertEquals(3, usage.get("outputTokens"));
        assertEquals(1, usage.get("cachedTokens"));
        assertEquals(0.4, ((Number) usage.get("time")).doubleValue(), 1e-9);
        assertEquals(15, usage.get("totalTokens"));
    }

    @Test
    void encode_agentResultWithoutUsage_shouldEmitNothing() {
        A2uiV091Encoder encoder = new A2uiV091Encoder("s1", null, true);
        encoder.bootstrap();
        Event result = new Event(EventType.AGENT_RESULT, Msg.builder()
                .content(TextBlock.builder().text("你好").build())
                .build(), true);
        assertTrue(encoder.encode(result).isEmpty());
    }

    @Test
    void encode_cumulativeChunk_shouldNotDuplicate() {
        A2uiV091Encoder encoder = new A2uiV091Encoder(null, null, false);
        encoder.bootstrap();
        Event c1 = new Event(EventType.REASONING, Msg.builder()
                .content(TextBlock.builder().text("Hel").build())
                .build(), false);
        Event c2 = new Event(EventType.REASONING, Msg.builder()
                .content(TextBlock.builder().text("Hello").build())
                .build(), false);
        assertEquals("Hel", dataModelValue(encoder.encode(c1).get(0)));
        assertEquals("Hello", dataModelValue(encoder.encode(c2).get(0)));
    }

    @Test
    void bootstrap_twice_shouldBeIdempotentEmpty() {
        A2uiV091Encoder encoder = new A2uiV091Encoder("x", null, true);
        assertFalse(encoder.bootstrap().isEmpty());
        assertTrue(encoder.bootstrap().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Object dataModelValue(Map<String, Object> envelope) {
        Map<String, Object> body = (Map<String, Object>) envelope.get("updateDataModel");
        return body.get("value");
    }

    @SuppressWarnings("unchecked")
    private static Object dataModelPath(Map<String, Object> envelope) {
        Map<String, Object> body = (Map<String, Object>) envelope.get("updateDataModel");
        return body.get("path");
    }
}
