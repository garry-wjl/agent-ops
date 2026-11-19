package ink.garry.rd.agent.ws.adapter.debugconsole;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.common.AgentInvokeSseMapper;
import ink.garry.rd.agent.ws.adapter.common.SseKeepAlive;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.UserInputHitlService;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunCoordinator;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import ink.garry.rd.agent.ws.client.agentrunner.StructuredInvokeVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.debugconsole.DebugInvokeRequest;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 调试台调用控制器（SSE 流式，支持断线续传）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/debug-console")
@RequiredArgsConstructor
public class DebugInvokeController extends BaseController {

    private final AgentRunCoordinator agentRunCoordinator;
    private final InvokeContentNormalizer invokeContentNormalizer;
    private final UserInputHitlService userInputHitlService;
    private final AgentInvokeService agentInvokeService;

    /**
     * 成功时 text/event-stream；进入 SSE 前的业务/参数异常须能协商为 application/json。
     */
    @PostMapping(value = "/invoke", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8",
            MediaType.APPLICATION_JSON_VALUE
    })
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody DebugInvokeRequest req,
                                                HttpServletRequest httpReq) {
        agentInvokeService.rejectStreamIfStructured(req.getAgentNum(), req.getTargetVersion());
        String lastEventId = firstNonBlank(req.getLastEventId(), httpReq.getHeader("Last-Event-ID"));
        boolean resume = Boolean.TRUE.equals(req.getResume())
                || (StrUtil.isNotBlank(lastEventId) && isBlankInvoke(req));

        NormalizedInvokeContent content = resume
                ? NormalizedInvokeContent.builder().text(null).build()
                : invokeContentNormalizer.normalize(req.getInput(), req.getAttachments());

        return SseKeepAlive.withHeartbeat(
                AgentInvokeSseMapper.toSseFromRun(
                        agentRunCoordinator.invokeOrResume(
                                resume,
                                lastEventId,
                                req.getRunId(),
                                req.getAgentNum(),
                                content,
                                req.getSessionNum(),
                                getCurrentUserId(),
                                req.getTargetVersion(),
                                "DEBUG_CONSOLE",
                                req.getContext())));
    }

    /**
     * 调试台同步 JSON 调用。仅当所选版本开启结构化输出时使用。
     *
     * @param req 调试请求
     * @return 符合 Schema 的 JSON、会话、runId 与 Token
     */
    @PostMapping("/invokeJson")
    public Result<StructuredInvokeVO> invokeJson(@Valid @RequestBody DebugInvokeRequest req) {
        if (Boolean.TRUE.equals(req.getResume())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "结构化输出接口不支持续传");
        }
        StructuredInvokeVO vo = agentInvokeService.invokeJson(
                req.getAgentNum(),
                req.getInput(),
                req.getAttachments(),
                req.getSessionNum(),
                getCurrentUserId(),
                req.getTargetVersion(),
                req.getContext());
        return Result.ok(vo);
    }

    /**
     * 用户表单 HITL 回传：提交或取消后唤醒挂起的 Agent 续跑。
     * <p>
     * Body 推荐 snake_case（{@code request_id} / {@code session_num} / {@code run_id}）；
     * 契约见 {@code docs/design-docs/2026-10-17-user-form-input-hitl.md}。
     */
    @PostMapping("/user-input/respond")
    public Result<Void> respondUserInput(@RequestBody UserInputRespondParam param) {
        userInputHitlService.respond(param);
        return Result.ok();
    }

    private static boolean isBlankInvoke(DebugInvokeRequest req) {
        boolean noInput = req.getInput() == null
                || (req.getInput() instanceof String s && StrUtil.isBlank(s));
        boolean noAtt = req.getAttachments() == null || req.getAttachments().isEmpty();
        return noInput && noAtt;
    }

    private static String firstNonBlank(String a, String b) {
        if (StrUtil.isNotBlank(a)) {
            return a.trim();
        }
        if (StrUtil.isNotBlank(b)) {
            return b.trim();
        }
        return null;
    }
}
