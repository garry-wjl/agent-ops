package ink.garry.rd.agent.ws.application.a2ui;

import ink.garry.rd.agent.ws.client.agentrunner.UserInputRequiredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2uiUserFormEncoderTest {

    @Test
    @DisplayName("编码表单含 submit/cancel action 与 hitl 元数据")
    void encodeForm_containsActionsAndHitlMeta() {
        UserInputRequiredEvent event = UserInputRequiredEvent.builder()
                .requestId("UIR1")
                .replyId("r1")
                .toolCallId("c1")
                .runId("RUN1")
                .sessionId("SES1")
                .form(Map.of(
                        "title", "确认",
                        "fields", List.of(Map.of(
                                "name", "city",
                                "label", "城市",
                                "type", "string"))))
                .build();
        List<Map<String, Object>> msgs = A2uiUserFormEncoder.encodeForm("main", event);
        assertThat(msgs).hasSize(2);
        String joined = msgs.toString();
        assertThat(joined).contains("submit_user_input");
        assertThat(joined).contains("cancel_user_input");
        assertThat(joined).contains("UIR1");
        assertThat(joined).contains("city");
    }
}
