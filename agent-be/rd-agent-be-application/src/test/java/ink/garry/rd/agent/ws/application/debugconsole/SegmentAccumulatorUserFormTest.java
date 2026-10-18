package ink.garry.rd.agent.ws.application.debugconsole;

import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentAccumulatorUserFormTest {

    @Test
    @DisplayName("表单段插在已有正文之后，续写文本落在表单后面")
    void appendUserForm_sitsBetweenExistingTextAndLaterText() {
        SegmentAccumulator acc = new SegmentAccumulator();
        acc.appendText("挂起前");
        acc.appendUserForm(
                Map.of(
                        "title", "补全",
                        "fields", List.of(Map.of("name", "city", "label", "城市", "type", "string"))),
                "UIR1",
                "submitted",
                Map.of("city", "深圳"));
        acc.appendText("续跑后");

        List<AssistantSegment> segments = acc.getSegments();
        assertThat(segments).extracting(AssistantSegment::getKind)
                .containsExactly("text", "user_form", "text");
        AssistantSegment form = segments.get(1);
        assertThat(form.getStatus()).isEqualTo("submitted");
        assertThat(form.getForm()).containsEntry("requestId", "UIR1");
        assertThat(form.getForm()).containsEntry("title", "补全");
        assertThat(form.getValues()).containsEntry("city", "深圳");
        assertThat(form.getFields()).hasSize(1);
    }

    @Test
    @DisplayName("同一 requestId 不重复插入")
    void appendUserForm_dedupesByRequestId() {
        SegmentAccumulator acc = new SegmentAccumulator();
        acc.appendUserForm(Map.of("title", "A", "fields", List.of()), "UIR1", "submitted", Map.of("city", "深圳"));
        acc.appendUserForm(Map.of("title", "A", "fields", List.of()), "UIR1", "submitted", Map.of("city", "北京"));
        assertThat(acc.getSegments()).hasSize(1);
        assertThat(acc.getSegments().get(0).getValues()).containsEntry("city", "深圳");
    }
}
