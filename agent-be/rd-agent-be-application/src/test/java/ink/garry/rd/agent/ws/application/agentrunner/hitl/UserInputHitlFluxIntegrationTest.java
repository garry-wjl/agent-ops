package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟 AgentRunner handleUserFormSuspend：required → await → respond → 续跑。
 */
class UserInputHitlFluxIntegrationTest {

    @Test
    @DisplayName("方案 A：同流等待 respond 后再续跑")
    void awaitThenResumeOnSameFlow() throws Exception {
        UserInputHitlService service = new UserInputHitlService();
        ToolUseBlock tool = ToolUseBlock.builder()
                .id("call-flux")
                .name(RequestUserInputTool.NAME)
                .input(Map.of("title", "补全", "fields", List.of(Map.of("name", "city", "type", "string"))))
                .build();
        UserInputHitlService.Pending pending = service.register(
                "SES-F", "RUN-F", "reply-f", tool, Map.of("title", "补全"));

        AtomicReference<UserInputHitlService.Answer> answerRef = new AtomicReference<>();
        Thread waiter = new Thread(() -> answerRef.set(
                service.awaitAnswer(pending.getRequestId()).block()));
        waiter.start();
        Thread.sleep(80);

        UserInputRespondParam p = new UserInputRespondParam();
        p.setRequestId(pending.getRequestId());
        p.setSessionNum("SES-F");
        p.setRunId("RUN-F");
        p.setAction("submit");
        p.setValues(Map.of("city", "深圳"));
        service.respond(p);

        waiter.join(TimeUnit.SECONDS.toMillis(3));
        assertThat(answerRef.get()).isNotNull();
        assertThat(answerRef.get().getAction()).isEqualTo("submit");
        assertThat(answerRef.get().getValues()).containsEntry("city", "深圳");
        String payload = UserInputHitlService.toToolResult(pending, answerRef.get())
                .getOutput().get(0).toString();
        assertThat(payload).contains("submitted").contains("深圳");
    }
}
