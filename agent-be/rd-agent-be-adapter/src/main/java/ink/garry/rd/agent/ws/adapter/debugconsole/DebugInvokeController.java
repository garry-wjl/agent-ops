package ink.garry.rd.agent.ws.adapter.debugconsole;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.common.AgentInvokeSseMapper;
import ink.garry.rd.agent.ws.adapter.common.SseKeepAlive;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunCoordinator;
import ink.garry.rd.agent.ws.client.debugconsole.DebugInvokeRequest;
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

    /**
     * 成功时 text/event-stream；进入 SSE 前的业务/参数异常须能协商为 application/json。
     */
    @PostMapping(value = "/invoke", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8",
            MediaType.APPLICATION_JSON_VALUE
    })
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody DebugInvokeRequest req,
                                                HttpServletRequest httpReq) {
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
