package ink.garry.rd.agent.ws.adapter.open;

import ink.garry.rd.agent.ws.adapter.common.SseEventTransformer;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.security.ApiKeyAuthenticationFilter;
import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.client.agent.OpenInvokeParam;
import ink.garry.rd.agent.ws.client.agent.OpenSessionCreateParam;
import ink.garry.rd.agent.ws.client.agent.OpenSessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对外调用（open）控制器（秘钥 Bearer 认证）。
 * <p>
 * 认证由 {@link ApiKeyAuthenticationFilter} 完成并注入权威归属（{@code openAgentNum / openWorkspaceNum /
 * openApiKeyNum}）。本控制器：
 * <ul>
 *   <li>用注入的权威 {@code openAgentNum} 校验 body.agentNum 一致性（不一致 → 403 {@code API_KEY_AGENT_MISMATCH}）；</li>
 *   <li>{@code operatorId} 由 application 兜底为 system；</li>
 *   <li>仅调 {@link OpenAgentInvokeService} 一层，不写业务逻辑。</li>
 * </ul>
 * 返回 SSE 流；对于 {@code format_json} 工具的结果，自动将
 * {@code {"type":"text","text":"{...}"}} 替换为
 * {@code {"type":"object","value":<parsed JSON>}}，
 * 方便调用方直接从流中获取结构化 JSON 对象。
 * <p>
 * 不继承登录态（open 无登录用户），故不使用 {@link BaseController#getCurrentUserId()}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/open/agents")
@RequiredArgsConstructor
public class OpenAgentController {

    private final OpenAgentInvokeService openAgentInvokeService;
    private final ObjectMapper objectMapper;

    /**
     * 对外流式调用 Agent（SSE）。
     *
     * @param param 调用参数（agentNum/input/sessionNum/operatorId）
     * @param req   注入权威归属的请求
     * @return Event 流（text/event-stream）
     */
    @PostMapping(value = "/command/invoke", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody OpenInvokeParam param, HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        return openAgentInvokeService.invoke(
                        param.getAgentNum(), param.getInput(), param.getSessionNum(), param.getOperatorId())
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

    // ... rest of the methods remain unchanged

    /**
     * 对外创建会话。
     *
     * @param param 建会话参数
     * @param req   注入权威归属的请求
     * @return 新建会话 DTO
     */
    @PostMapping("/command/createSession")
    public Result<SessionDTO> createSession(@Valid @RequestBody OpenSessionCreateParam param,
                                            HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        SessionDTO dto = openAgentInvokeService.createSession(
                param.getAgentNum(), param.getSkillHint(), param.getTitle(), param.getOperatorId());
        return Result.ok(dto);
    }

    /**
     * 对外会话分页列表。
     *
     * @param query 列表查询（agentNum + 分页 + operatorId）
     * @param req   注入权威归属的请求
     * @return 分页结果
     */
    @PostMapping("/query/sessionList")
    public Result<PageVO<SessionListVO>> sessionList(@Valid @RequestBody OpenSessionListQuery query,
                                                     HttpServletRequest req) {
        assertAgentMatch(query.getAgentNum(), req);
        SessionListQuery inner = new SessionListQuery();
        inner.setAgentNum(query.getAgentNum());
        inner.setPageNo(query.getPageNo());
        inner.setPageSize(query.getPageSize());
        return Result.ok(openAgentInvokeService.listSessions(inner));
    }

    /**
     * 对外会话详情（含消息链）。
     * <p>
     * GET 无 agentNum 入参，归属由秘钥隐含；会话可见性为 Agent 维度，详情仅校验会话存在性。
     *
     * @param num 会话业务编号
     * @return 会话详情 VO
     */
    @GetMapping("/query/sessionDetail")
    public Result<SessionDetailVO> sessionDetail(@RequestParam("num") String num) {
        return Result.ok(openAgentInvokeService.sessionDetail(num));
    }

    /**
     * 校验 body 的 agentNum 与秘钥隐含的权威 agentNum 一致；不一致抛 403 API_KEY_AGENT_MISMATCH。
     *
     * @param bodyAgentNum 请求体携带的 agentNum
     * @param req          含过滤器注入的权威 agentNum 的请求
     */
    private void assertAgentMatch(String bodyAgentNum, HttpServletRequest req) {
        Object authoritative = req.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM);
        if (authoritative == null || !authoritative.equals(bodyAgentNum)) {
            throw new BusinessException(BizCode.API_KEY_AGENT_MISMATCH.getCode(),
                    "秘钥与该 Agent 不匹配");
        }
    }
}
