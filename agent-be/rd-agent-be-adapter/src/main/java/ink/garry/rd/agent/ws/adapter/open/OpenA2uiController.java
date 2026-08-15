package ink.garry.rd.agent.ws.adapter.open;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.adapter.security.ApiKeyAuthenticationFilter;
import ink.garry.rd.agent.ws.application.a2ui.A2uiV091Encoder;
import ink.garry.rd.agent.ws.application.a2ui.OpenA2uiInvokeService;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiInvokeParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 对外 A2UI v0.9.1 协议接口（秘钥 Bearer 认证）。
 * <p>
 * 与 {@link OpenAgentController} 的 AgentScope Event SSE（{@code /command/invoke}）并存：
 * <ul>
 *   <li>{@code POST /command/a2ui/invoke} — 用户输入 → A2UI 渲染流</li>
 *   <li>{@code POST /command/a2ui/action} — 客户端 action 回传 → A2UI 渲染流</li>
 * </ul>
 * 认证由 {@link ApiKeyAuthenticationFilter} 完成；SSE 每条 {@code data} 为一个完整 A2UI envelope JSON。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/open/agents")
@RequiredArgsConstructor
public class OpenA2uiController {

    private final OpenA2uiInvokeService openA2uiInvokeService;
    private final ObjectMapper objectMapper;

    /**
     * A2UI 流式调用（用户输入）。
     *
     * @param param 调用参数
     * @param req   注入权威归属的请求
     * @return A2UI envelope SSE
     */
    @PostMapping(value = "/command/a2ui/invoke", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody OpenA2uiInvokeParam param,
                                                HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        return toSse(openA2uiInvokeService.invoke(param));
    }

    /**
     * A2UI action 回传（客户端交互）。
     *
     * @param param action 参数
     * @param req   注入权威归属的请求
     * @return A2UI envelope SSE
     */
    @PostMapping(value = "/command/a2ui/action", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> action(@Valid @RequestBody OpenA2uiActionParam param,
                                                HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        return toSse(openA2uiInvokeService.action(param));
    }

    private Flux<ServerSentEvent<String>> toSse(Flux<Map<String, Object>> messages) {
        return messages.map(envelope -> {
            try {
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(envelope))
                        .build();
            } catch (Exception e) {
                log.error("A2UI SSE 序列化失败", e);
                return ServerSentEvent.<String>builder()
                        .data(A2uiV091Encoder.toJson(Map.of(
                                "version", "v0.9.1",
                                "error", Map.of("message", "serialize_failed"))))
                        .build();
            }
        });
    }

    /**
     * 校验 body 的 agentNum 与秘钥隐含的权威 agentNum 一致。
     *
     * @param bodyAgentNum 请求体 agentNum
     * @param req          含过滤器注入属性的请求
     */
    private void assertAgentMatch(String bodyAgentNum, HttpServletRequest req) {
        Object authoritative = req.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM);
        if (authoritative == null || !authoritative.equals(bodyAgentNum)) {
            throw new BusinessException(BizCode.API_KEY_AGENT_MISMATCH.getCode(),
                    "秘钥与该 Agent 不匹配");
        }
    }
}
