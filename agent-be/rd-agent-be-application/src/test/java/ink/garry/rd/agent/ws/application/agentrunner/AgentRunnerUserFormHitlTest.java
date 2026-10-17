package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.application.agentrunner.hitl.RequestUserInputTool;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunnerUserFormHitlTest {

    @Test
    @DisplayName("TOOL_SUSPENDED + request_user_input 识别为表单挂起")
    void detectsUserFormSuspend() {
        ToolUseBlock tool = ToolUseBlock.builder()
                .id("c1")
                .name(RequestUserInputTool.NAME)
                .input(Map.of("title", "x", "fields", List.of()))
                .build();
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(tool)
                .generateReason(GenerateReason.TOOL_SUSPENDED)
                .build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true, null);
        assertThat(AgentRunnerService.isUserFormSuspended(event)).isTrue();
        assertThat(AgentRunnerService.extractRequestUserInputTool(msg).getName())
                .isEqualTo(RequestUserInputTool.NAME);
    }

    @Test
    @DisplayName("普通 AGENT_RESULT 不是表单挂起")
    void normalResult_notSuspend() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("done").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true, null);
        assertThat(AgentRunnerService.isUserFormSuspended(event)).isFalse();
    }
}
