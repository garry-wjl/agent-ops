package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentMsgFactory} 落库载荷单测。
 */
class AgentMsgFactoryPersistTest {

    @Test
    void toPersistPayload_textOnly() {
        AgentMsgFactory factory = new AgentMsgFactory();
        NormalizedInvokeContent content = NormalizedInvokeContent.builder()
                .text("hi")
                .attachments(List.of())
                .build();
        AgentMsgFactory.PersistPayload p = factory.toPersistPayload(content);
        assertEquals("hi", p.contentText());
        assertEquals(InputType.TEXT, p.inputType());
    }

    @Test
    void toPersistPayload_multimodalJson() {
        AgentMsgFactory factory = new AgentMsgFactory();
        NormalizedInvokeContent content = NormalizedInvokeContent.builder()
                .text("see")
                .attachments(List.of(NormalizedInvokeContent.AttachmentRef.builder()
                        .fileId("f1")
                        .name("a.png")
                        .mimeType("image/png")
                        .size(10L)
                        .kind(AttachmentKind.IMAGE)
                        .build()))
                .build();
        AgentMsgFactory.PersistPayload p = factory.toPersistPayload(content);
        assertEquals(InputType.MULTIMODAL, p.inputType());
        assertTrue(p.contentText().contains("\"fileId\":\"f1\""));
        assertTrue(p.contentText().contains("\"kind\":\"image\""));
    }
}
