package ink.garry.rd.agent.ws.infra.session.entity;

import ink.garry.rd.agent.ws.domain.session.Message;
import ink.garry.rd.agent.ws.domain.session.valueobject.MessageRole;
import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 消息行与领域对象之间，Run ID 和 Token 列要来回保持一致。
 */
class MessageEntityTurnUsageTest {

    @Test
    void roundTrip_keepsRunIdAndTokens() {
        Message message = new Message();
        message.setNum("MSG-1");
        message.setSessionNum("SES-1");
        message.setRole(MessageRole.ASSISTANT);
        message.setContent("ok");
        message.setRunId("RUN-1");
        message.setTurnTokenUsage(TurnTokenUsage.builder()
                .inputTokens(10)
                .outputTokens(4)
                .cachedTokens(1)
                .totalTokens(14)
                .build());

        Message restored = MessageEntity.toDomain(MessageEntity.fromDomain(message));

        assertEquals("RUN-1", restored.getRunId());
        assertEquals(10, restored.getTurnTokenUsage().getInputTokens());
        assertEquals(4, restored.getTurnTokenUsage().getOutputTokens());
        assertEquals(1, restored.getTurnTokenUsage().getCachedTokens());
        assertEquals(14, restored.getTurnTokenUsage().getTotalTokens());
    }

    @Test
    void roundTrip_blankUsageStaysNull() {
        Message message = new Message();
        message.setNum("MSG-1");
        message.setSessionNum("SES-1");
        message.setRole(MessageRole.USER);
        message.setContent("hi");

        Message restored = MessageEntity.toDomain(MessageEntity.fromDomain(message));

        assertNull(restored.getRunId());
        assertNull(restored.getTurnTokenUsage());
    }
}
