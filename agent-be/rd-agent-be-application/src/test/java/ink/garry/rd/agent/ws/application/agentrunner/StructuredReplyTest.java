package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化回复落库时保留工具段，正文只留 JSON。
 */
class StructuredReplyTest {

    @Test
    void keepsToolSegmentsAndAppendsJsonText() {
        AssistantSegment tool = AssistantSegment.builder()
                .kind("tool_use")
                .toolName("lookup")
                .build();
        AssistantSegment thinking = AssistantSegment.builder()
                .kind("thinking")
                .text("先想一下")
                .build();
        List<AssistantSegment> segments = StructuredReply.segmentsWithJson(
                List.of(thinking, tool), "{\"title\":\"例\"}");
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getKind()).isEqualTo("tool_use");
        assertThat(segments.get(1).getKind()).isEqualTo("text");
        assertThat(segments.get(1).getText()).isEqualTo("{\"title\":\"例\"}");
    }
}
