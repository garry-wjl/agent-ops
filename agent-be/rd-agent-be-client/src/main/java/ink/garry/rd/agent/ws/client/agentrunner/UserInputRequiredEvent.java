package ink.garry.rd.agent.ws.client.agentrunner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SSE {@code event: user_input.required} 载荷：Agent 暂停等待用户填表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInputRequiredEvent {

    public static final String SSE_EVENT = "user_input.required";
    public static final String SSE_ACCEPTED = "user_input.accepted";
    public static final String SSE_REJECTED = "user_input.rejected";

    public static final String TOOL_NAME = "request_user_input";

    private String runId;
    private String sessionId;
    private String replyId;
    private String requestId;
    private String toolCallId;
    private String toolName;

    /** 工具入参（title / fields 等），原样透出供前端渲染 */
    private Map<String, Object> form;
}
