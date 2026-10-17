package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户表单 HITL：挂起等待 + respond 唤醒。
 */
@Slf4j
@Service
public class UserInputHitlService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, Pending> pendings = new ConcurrentHashMap<>();

    /**
     * 注册一次待填表请求。
     */
    public Pending register(String sessionNum, String runId, String replyId, ToolUseBlock toolCall,
                            Map<String, Object> form) {
        String requestId = "UIR" + IdUtil.getSnowflakeNextIdStr();
        Sinks.One<Answer> sink = Sinks.one();
        Pending pending = Pending.builder()
                .requestId(requestId)
                .sessionNum(sessionNum)
                .runId(runId)
                .replyId(replyId)
                .toolCall(toolCall)
                .form(form == null ? Map.of() : form)
                .sink(sink)
                .build();
        pendings.put(requestId, pending);
        return pending;
    }

    /**
     * 阻塞等待用户回传（在 Reactor 链中使用）。
     */
    public Mono<Answer> awaitAnswer(String requestId) {
        Pending pending = pendings.get(requestId);
        if (pending == null) {
            return Mono.error(new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "表单请求不存在或已失效: " + requestId));
        }
        return pending.sink.asMono()
                .timeout(DEFAULT_TIMEOUT)
                .doFinally(sig -> pendings.remove(requestId));
    }

    /**
     * 用户提交或取消。
     */
    public void respond(UserInputRespondParam param) {
        if (param == null || StrUtil.isBlank(param.getRequestId())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "requestId 不能为空");
        }
        Pending pending = pendings.get(param.getRequestId());
        if (pending == null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "表单请求不存在或已处理: " + param.getRequestId());
        }
        String action = StrUtil.blankToDefault(param.getAction(), "").trim().toLowerCase();
        if (!"submit".equals(action) && !"cancel".equals(action)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "action 须为 submit 或 cancel");
        }
        if (StrUtil.isNotBlank(param.getRunId()) && StrUtil.isNotBlank(pending.getRunId())
                && !param.getRunId().equals(pending.getRunId())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "runId 与挂起请求不一致");
        }
        String session = param.resolveSessionNum();
        if (StrUtil.isNotBlank(session) && StrUtil.isNotBlank(pending.getSessionNum())
                && !session.equals(pending.getSessionNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "session 与挂起请求不一致");
        }

        Answer answer = Answer.builder()
                .requestId(pending.getRequestId())
                .action(action)
                .values(param.getValues() == null ? Map.of() : param.getValues())
                .build();
        Sinks.EmitResult result = pending.sink.tryEmitValue(answer);
        if (result.isFailure()) {
            log.warn("[user-input-hitl] emit failed requestId={} result={}",
                    param.getRequestId(), result);
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "表单请求已关闭或已应答");
        }
    }

    /**
     * 将 Answer 转为 ToolResultBlock（供 Agent 续跑）。
     */
    public static ToolResultBlock toToolResult(Pending pending, Answer answer) {
        Map<String, Object> body = new LinkedHashMap<>();
        Object formId = pending.getForm().get("form_id");
        if (formId != null) {
            body.put("form_id", formId);
        }
        if ("cancel".equals(answer.getAction())) {
            body.put("status", "cancelled");
            body.put("reason", "user_cancelled");
        } else {
            body.put("status", "submitted");
            body.put("values", answer.getValues());
        }
        String json = JSON.toJSONString(body);
        ToolUseBlock tc = pending.getToolCall();
        return ToolResultBlock.builder()
                .id(tc.getId())
                .name(tc.getName())
                .output(java.util.List.of(TextBlock.builder().text(json).build()))
                .state(ToolResultState.SUCCESS)
                .build();
    }

    @Getter
    @Builder
    public static class Pending {
        private final String requestId;
        private final String sessionNum;
        private final String runId;
        private final String replyId;
        private final ToolUseBlock toolCall;
        private final Map<String, Object> form;
        private final Sinks.One<Answer> sink;
    }

    @Getter
    @Builder
    public static class Answer {
        private final String requestId;
        private final String action;
        private final Map<String, Object> values;
    }
}
