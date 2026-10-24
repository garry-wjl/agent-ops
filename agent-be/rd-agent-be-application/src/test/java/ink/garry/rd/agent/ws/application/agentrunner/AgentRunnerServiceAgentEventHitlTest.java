package ink.garry.rd.agent.ws.application.agentrunner;

import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.message.ToolUseBlock;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.RequestUserInputTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * streamEvents HITL 判定：RequireExternalExecution + request_user_input。
 */
class AgentRunnerServiceAgentEventHitlTest {

    @Test
    void detectsUserFormExternalRequired() {
        ToolUseBlock form = ToolUseBlock.builder()
                .id("call-1")
                .name(RequestUserInputTool.NAME)
                .input(Map.of("title", "填写", "fields", List.of()))
                .build();
        RequireExternalExecutionEvent event =
                new RequireExternalExecutionEvent("reply-1", List.of(form));

        assertTrue(AgentRunnerService.isUserFormExternalRequired(event));
        assertNotNull(AgentRunnerService.extractRequestUserInputFromAgentEvent(event));
        assertFalse(AgentRunnerService.isTerminalAgentResultV2(event));
    }

    @Test
    void ignoresUnrelatedExternalTools() {
        ToolUseBlock other = ToolUseBlock.builder()
                .id("call-2")
                .name("shell")
                .input(Map.of("cmd", "ls"))
                .build();
        RequireExternalExecutionEvent event =
                new RequireExternalExecutionEvent("reply-2", List.of(other));

        assertFalse(AgentRunnerService.isUserFormExternalRequired(event));
    }
}
