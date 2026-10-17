package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInputHitlServiceTest {

    private UserInputHitlService service;

    @BeforeEach
    void setUp() {
        service = new UserInputHitlService();
    }

    @Test
    @DisplayName("submit 唤醒 await 并生成 ToolResult")
    void submit_wakesAwait() throws Exception {
        ToolUseBlock tool = ToolUseBlock.builder()
                .id("call-1")
                .name(RequestUserInputTool.NAME)
                .input(Map.of("title", "t", "fields", java.util.List.of()))
                .build();
        UserInputHitlService.Pending pending = service.register(
                "SES1", "RUN1", "reply-1", tool, Map.of("form_id", "f1", "title", "t"));

        AtomicReference<UserInputHitlService.Answer> answerRef = new AtomicReference<>();
        Thread waiter = new Thread(() -> answerRef.set(
                service.awaitAnswer(pending.getRequestId()).block()));
        waiter.start();
        Thread.sleep(50);

        UserInputRespondParam p = new UserInputRespondParam();
        p.setRequestId(pending.getRequestId());
        p.setRunId("RUN1");
        p.setSessionNum("SES1");
        p.setAction("submit");
        p.setValues(Map.of("name", "garry"));
        service.respond(p);

        waiter.join(TimeUnit.SECONDS.toMillis(3));
        assertThat(answerRef.get()).isNotNull();
        assertThat(answerRef.get().getAction()).isEqualTo("submit");
        assertThat(answerRef.get().getValues()).containsEntry("name", "garry");
        ToolResultBlock result = UserInputHitlService.toToolResult(pending, answerRef.get());
        assertThat(result.getId()).isEqualTo("call-1");
        assertThat(result.getName()).isEqualTo(RequestUserInputTool.NAME);
    }

    @Test
    @DisplayName("cancel 写入 cancelled 状态")
    void cancel_buildsCancelledPayload() {
        ToolUseBlock tool = ToolUseBlock.builder()
                .id("call-2")
                .name(RequestUserInputTool.NAME)
                .input(Map.of())
                .build();
        UserInputHitlService.Pending pending = service.register(
                "SES1", "RUN1", "reply-1", tool, Map.of("form_id", "f1"));
        UserInputHitlService.Answer answer = UserInputHitlService.Answer.builder()
                .requestId(pending.getRequestId())
                .action("cancel")
                .values(Map.of())
                .build();
        ToolResultBlock result = UserInputHitlService.toToolResult(pending, answer);
        String text = result.getOutput().get(0).toString();
        assertThat(text).contains("cancelled");
        assertThat(text).contains("user_cancelled");
    }

    @Test
    @DisplayName("未知 requestId 抛业务异常")
    void respond_unknownRequest_throws() {
        UserInputRespondParam p = new UserInputRespondParam();
        p.setRequestId("nope");
        p.setAction("submit");
        assertThatThrownBy(() -> service.respond(p)).isInstanceOf(BusinessException.class);
    }
}
