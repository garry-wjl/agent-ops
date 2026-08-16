package ink.garry.rd.agent.ws.adapter.debugconsole;

import ink.garry.rd.agent.ws.adapter.common.SseEventTransformer;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.client.debugconsole.DebugInvokeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 调试台调用控制器（SSE 流式）。
 * <p>
 * 附件校验失败在进入 SSE 之前以业务异常返回 HTTP 4xx。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/debug-console")
@RequiredArgsConstructor
public class DebugInvokeController extends BaseController {

    private final AgentInvokeService agentInvokeService;
    private final InvokeContentNormalizer invokeContentNormalizer;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/invoke", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody DebugInvokeRequest req) {
        invokeContentNormalizer.normalize(req.getInput(), req.getAttachments());
        return agentInvokeService.invokeStream(
                        req.getAgentNum(), req.getInput(), req.getAttachments(),
                        req.getSessionNum(), getCurrentUserId(), req.getTargetVersion(), req.getContext())
                .map(event -> {
                    try {
                        JsonNode root = objectMapper.valueToTree(event);
                        SseEventTransformer.transformFormatJsonResults(root);
                        return ServerSentEvent.<String>builder()
                                .data(objectMapper.writeValueAsString(root))
                                .build();
                    } catch (Exception e) {
                        log.error("SSE 事件变换失败", e);
                        return ServerSentEvent.<String>builder()
                                .data("{}")
                                .build();
                    }
                });
    }
}
