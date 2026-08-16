package ink.garry.rd.agent.ws.adapter.open;

import ink.garry.rd.agent.ws.adapter.common.SseEventTransformer;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.security.ApiKeyAuthenticationFilter;
import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.client.agent.OpenInvokeParam;
import ink.garry.rd.agent.ws.client.agent.OpenSessionCreateParam;
import ink.garry.rd.agent.ws.client.agent.OpenSessionListQuery;
import ink.garry.rd.agent.ws.client.attachment.OpenUploadAttachmentParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.common.oss.OssPresignResultVO;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
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
 * 返回 SSE 流；附件校验在进入 SSE 之前完成（业务异常 → HTTP 4xx）。
 * <p>
 * 不继承登录态（open 无登录用户），故不使用 {@link BaseController#getCurrentUserId()}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/open/agents")
@RequiredArgsConstructor
public class OpenAgentController {

    private final OpenAgentInvokeService openAgentInvokeService;
    private final InvokeContentNormalizer invokeContentNormalizer;
    private final ObjectMapper objectMapper;

    /**
     * 开放上传附件（预签名 + 登记）。
     *
     * @param param 上传参数
     * @param req   注入权威归属的请求
     * @return 预签名结果
     */
    @PostMapping("/command/uploadAttachment")
    public Result<OssPresignResultVO> uploadAttachment(@Valid @RequestBody OpenUploadAttachmentParam param,
                                                       HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        String workspaceNum = bindOpenWorkspace(req);
        OssPresignResultVO vo = openAgentInvokeService.uploadAttachment(param, workspaceNum, null);
        return Result.ok(vo);
    }

    /**
     * 对外流式调用 Agent（SSE）。
     * <p>
     * 附件 / 空内容校验在进入 SSE 之前完成（业务异常 → HTTP 4xx）。
     *
     * @param param 调用参数（agentNum/input/attachments/sessionNum/operatorId）
     * @param req   注入权威归属的请求
     * @return Event 流（text/event-stream）
     */
    @PostMapping(value = "/command/invoke", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody OpenInvokeParam param, HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        bindOpenWorkspace(req);
        invokeContentNormalizer.normalize(param.getInput(), param.getAttachments());
        return openAgentInvokeService.invoke(
                        param.getAgentNum(), param.getInput(), param.getAttachments(),
                        param.getSessionNum(), param.getOperatorId(), param.getContext())
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
        bindOpenWorkspace(req);
        SessionDTO dto = openAgentInvokeService.createSession(
                param.getAgentNum(), param.getSkillHint(), param.getTitle(),
                param.getOperatorId(), param.getContext());
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
        bindOpenWorkspace(req);
        SessionListQuery inner = new SessionListQuery();
        inner.setAgentNum(query.getAgentNum());
        inner.setPageNo(query.getPageNo());
        inner.setPageSize(query.getPageSize());
        return Result.ok(openAgentInvokeService.listSessions(inner));
    }

    /**
     * 对外会话详情（含消息链）。
     *
     * @param num 会话业务编号
     * @return 会话详情 VO
     */
    @GetMapping("/query/sessionDetail")
    public Result<SessionDetailVO> sessionDetail(@RequestParam("num") String num) {
        return Result.ok(openAgentInvokeService.sessionDetail(num));
    }

    private void assertAgentMatch(String bodyAgentNum, HttpServletRequest req) {
        Object authoritative = req.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_AGENT_NUM);
        if (authoritative == null || !authoritative.equals(bodyAgentNum)) {
            throw new BusinessException(BizCode.API_KEY_AGENT_MISMATCH.getCode(),
                    "秘钥与该 Agent 不匹配");
        }
    }

    /**
     * 将秘钥归属工作空间写入 ThreadLocal，供 Runner / ACL 使用。
     *
     * @param req 请求
     * @return workspaceNum
     */
    private String bindOpenWorkspace(HttpServletRequest req) {
        Object ws = req.getAttribute(ApiKeyAuthenticationFilter.ATTR_OPEN_WORKSPACE_NUM);
        if (ws == null || String.valueOf(ws).isBlank()) {
            throw new BusinessException(BizCode.API_KEY_INVALID.getCode(), "秘钥缺少工作空间归属");
        }
        String workspaceNum = String.valueOf(ws);
        WorkspaceContextHolder.set(WorkspaceContext.builder()
                .workspaceNum(workspaceNum)
                .role("API")
                .member(true)
                .build());
        return workspaceNum;
    }
}
