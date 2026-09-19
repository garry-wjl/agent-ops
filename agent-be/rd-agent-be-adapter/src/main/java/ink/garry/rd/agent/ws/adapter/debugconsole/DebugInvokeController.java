package ink.garry.rd.agent.ws.adapter.debugconsole;

import ink.garry.rd.agent.ws.adapter.common.SseEventTransformer;
import ink.garry.rd.agent.ws.adapter.common.SseKeepAlive;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.client.debugconsole.DebugInvokeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * 成功时 text/event-stream；进入 SSE 前的业务/参数异常须能协商为 application/json，
     * 否则 {@code produces} 仅 SSE 时全局异常处理返回 JSON 会内容协商失败 → 空 body HTTP 500。
     */
    @PostMapping(value = "/invoke", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8",
            MediaType.APPLICATION_JSON_VALUE
    })
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody DebugInvokeRequest req) {
        invokeContentNormalizer.normalize(req.getInput(), req.getAttachments());
        return SseKeepAlive.withHeartbeat(
                agentInvokeService.invokeStream(
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
                        }));
    }
}
