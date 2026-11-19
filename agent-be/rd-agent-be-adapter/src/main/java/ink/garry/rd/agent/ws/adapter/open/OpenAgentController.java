package ink.garry.rd.agent.ws.adapter.open;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.common.AgentInvokeSseMapper;
import ink.garry.rd.agent.ws.adapter.common.SseKeepAlive;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.security.ApiKeyAuthenticationFilter;
import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.UserInputHitlService;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunCoordinator;
import ink.garry.rd.agent.ws.client.agent.OpenInvokeParam;
import ink.garry.rd.agent.ws.client.agentrunner.StructuredInvokeVO;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    private final AgentRunCoordinator agentRunCoordinator;
    private final UserInputHitlService userInputHitlService;

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
    @PostMapping(value = "/command/invoke", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8",
            MediaType.APPLICATION_JSON_VALUE
    })
    public Flux<ServerSentEvent<String>> invoke(@Valid @RequestBody OpenInvokeParam param, HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        bindOpenWorkspace(req);
        openAgentInvokeService.rejectStreamIfStructured(param.getAgentNum());
        String lastEventId = firstNonBlank(param.getLastEventId(), req.getHeader("Last-Event-ID"));
        boolean resume = Boolean.TRUE.equals(param.getResume())
                || (StrUtil.isNotBlank(lastEventId) && isBlankOpenInvoke(param));
        NormalizedInvokeContent content = resume
                ? NormalizedInvokeContent.builder().text(null).build()
                : invokeContentNormalizer.normalize(param.getInput(), param.getAttachments());
        String operatorId = StrUtil.blankToDefault(param.getOperatorId(), "system");
        return SseKeepAlive.withHeartbeat(
                AgentInvokeSseMapper.toSseFromRun(
                        agentRunCoordinator.invokeOrResume(
                                resume,
                                lastEventId,
                                param.getRunId(),
                                param.getAgentNum(),
                                content,
                                param.getSessionNum(),
                                operatorId,
                                null,
                                "API",
                                param.getContext())));
    }

    /**
     * 结构化输出的同步调用。仅当 Agent 开启结构化输出时可用，响应为统一 Result。
     *
     * @param param 调用参数
     * @param req   注入权威归属的请求
     * @return 符合 Schema 的 JSON、会话、runId 与 Token
     */
    @PostMapping("/command/invokeJson")
    public Result<StructuredInvokeVO> invokeJson(@Valid @RequestBody OpenInvokeParam param,
                                                  HttpServletRequest req) {
        assertAgentMatch(param.getAgentNum(), req);
        bindOpenWorkspace(req);
        if (Boolean.TRUE.equals(param.getResume()) || StrUtil.isNotBlank(param.getLastEventId())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "结构化输出接口不支持续传");
        }
        StructuredInvokeVO vo = openAgentInvokeService.invokeJson(
                param.getAgentNum(),
                param.getInput(),
                param.getAttachments(),
                param.getSessionNum(),
                param.getOperatorId(),
                param.getContext());
        return Result.ok(vo);
    }

    private static boolean isBlankOpenInvoke(OpenInvokeParam param) {
        boolean noInput = StrUtil.isBlank(param.getInput());
        boolean noAtt = param.getAttachments() == null || param.getAttachments().isEmpty();
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

    /**
     * 用户表单 HITL 回传（开放面）。
     * <p>
     * 契约见设计文档 {@code docs/design-docs/2026-10-17-user-form-input-hitl.md}
     * 与前端 Agent 详情「API 信息」Tab。
     */
    @PostMapping("/command/user-input/respond")
    public Result<Void> respondUserInput(@RequestBody UserInputRespondParam param,
                                         HttpServletRequest req) {
        bindOpenWorkspace(req);
        userInputHitlService.respond(param);
        return Result.ok();
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
     * 对外会话详情。消息按页返回，缺省第 1 页、每页 200 条。
     *
     * @param num      会话业务编号
     * @param pageNo   页码，从 1 开始；空则第 1 页
     * @param pageSize 每页条数；空则 200，最大 200
     * @return 会话详情 VO
     */
    @GetMapping("/query/sessionDetail")
    public Result<SessionDetailVO> sessionDetail(@RequestParam("num") String num,
                                                 @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                 @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.ok(openAgentInvokeService.sessionDetail(num, pageNo, pageSize));
    }

    /**
     * 按 Run ID 查这一轮的对外消息（含本轮 Token）。
     * <p>
     * messages 和 turns 只含该 Run。秘钥只能读取归属 Agent 的会话。
     *
     * @param runId 本轮 Agent run 编号
     * @param req   注入权威归属的请求
     * @return 只含本轮消息的会话详情
     */
    @GetMapping("/query/sessionDetailByRun")
    public Result<SessionDetailVO> sessionDetailByRun(@RequestParam("runId") String runId,
                                                      HttpServletRequest req) {
        SessionDetailVO detail = openAgentInvokeService.sessionDetailByRunId(runId);
        assertAgentMatch(detail.getAgentNum(), req);
        return Result.ok(detail);
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
