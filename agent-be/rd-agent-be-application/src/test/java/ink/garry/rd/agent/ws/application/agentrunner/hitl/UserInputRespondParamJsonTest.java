package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserInputRespondParamJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("snake_case JSON 反序列化")
    void deserializesSnakeCase() throws Exception {
        String json = """
                {"session_num":"SES1","run_id":"RUN1","request_id":"UIR1",\
                "reply_id":"r1","tool_call_id":"c1","action":"submit",\
                "values":{"name":"garry"}}
                """;
        UserInputRespondParam p = mapper.readValue(json, UserInputRespondParam.class);
        assertThat(p.getSessionNum()).isEqualTo("SES1");
        assertThat(p.getRunId()).isEqualTo("RUN1");
        assertThat(p.getRequestId()).isEqualTo("UIR1");
        assertThat(p.getReplyId()).isEqualTo("r1");
        assertThat(p.getToolCallId()).isEqualTo("c1");
        assertThat(p.getAction()).isEqualTo("submit");
        assertThat(p.getValues()).containsEntry("name", "garry");
        assertThat(p.resolveSessionNum()).isEqualTo("SES1");
    }

    @Test
    @DisplayName("camelCase JSON 兼容")
    void deserializesCamelCase() throws Exception {
        String json = """
                {"sessionNum":"SES2","requestId":"UIR2","action":"cancel"}
                """;
        UserInputRespondParam p = mapper.readValue(json, UserInputRespondParam.class);
        assertThat(p.getSessionNum()).isEqualTo("SES2");
        assertThat(p.getRequestId()).isEqualTo("UIR2");
        assertThat(p.getAction()).isEqualTo("cancel");
    }
}
