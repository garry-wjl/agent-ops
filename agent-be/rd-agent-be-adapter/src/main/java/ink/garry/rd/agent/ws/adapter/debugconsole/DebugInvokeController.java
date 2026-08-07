package ink.garry.rd.agent.ws.adapter.debugconsole;

import cn.hutool.core.util.IdUtil;
import ink.garry.rd.agent.ws.adapter.common.SseEventTransformer;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.client.debugconsole.DebugInvokeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 调试台调用控制器（SSE 流式）。
 * <p>
 * 返回 SSE 流；对于 {@code format_json} 工具的结果，自动将
 * {@code {"type":"text","text":"{...}"}} 替换为
 * {@code {"type":"object","value":<parsed JSON>}}，
 * 方便调用方直接从流中获取结构化 JSON 对象。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/debug-console")
@RequiredArgsConstructor
public class DebugInvokeController extends BaseController {

    private final AgentInvokeService agentInvokeService;
    private final ObjectMapper objectMapper;


    @PostMapping(value = "/invoke", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody DebugInvokeRequest req) {
        return agentInvokeService.invokeStream(req.getAgentNum(), String.valueOf(req.getInput()), req.getSessionNum(), getCurrentUserId(), req.getTargetVersion())
                .map(event -> {
                    try {
                        // 1. 将 Event 序列化为 JSON 树
                        JsonNode root = objectMapper.valueToTree(event);
                        // 2. 对 format_json 工具结果进行对象化变换
                        SseEventTransformer.transformFormatJsonResults(root);
                        // 3. 写回字符串作为 SSE 数据
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
