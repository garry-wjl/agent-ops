package ink.garry.rd.agent.ws.client.agentrunner;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 用户表单 HITL 回传参数（调试台 / Open 共用）。
 * <p>
 * 与调试台 invoke 一致：JSON 主字段走 snake_case；同时兼容 camelCase（{@link JsonAlias}）。
 */
@Data
public class UserInputRespondParam {

    @JsonProperty("session_id")
    @JsonAlias("sessionId")
    private String sessionId;

    @JsonProperty("session_num")
    @JsonAlias("sessionNum")
    private String sessionNum;

    @JsonProperty("run_id")
    @JsonAlias("runId")
    private String runId;

    @JsonProperty("request_id")
    @JsonAlias("requestId")
    private String requestId;

    @JsonProperty("reply_id")
    @JsonAlias("replyId")
    private String replyId;

    @JsonProperty("tool_call_id")
    @JsonAlias("toolCallId")
    private String toolCallId;

    /** {@code submit} 或 {@code cancel} */
    private String action;

    /** submit 时的表单值；cancel 可空 */
    private Map<String, Object> values;

    public String resolveSessionNum() {
        return sessionNum != null && !sessionNum.isBlank() ? sessionNum : sessionId;
    }
}
