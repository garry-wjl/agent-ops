package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.application.session.SessionQueryService;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import io.agentscope.core.agent.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;

/**
 * 对外调用（open）编排服务：仅做"对外入口编排 + operator 解析"，不复制业务逻辑。
 * <p>
 * invoke 委托既有 {@link AgentInvokeService}，会话三方法委托既有
 * {@link SessionCommandService} / {@link SessionQueryService}；秘钥认证链路无登录用户，
 * 故 {@code operatorId} 为空时统一记 {@link #SYSTEM_OPERATOR}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAgentInvokeService {

    /** 对外调用无登录用户时的兜底操作人标识 */
    private static final String SYSTEM_OPERATOR = "system";

    private final SessionCommandService sessionCommandService;
    private final SessionQueryService sessionQueryService;
    private final AgentRunnerService agentRunnerService;

    /**
     * 对外流式调用 Agent；operatorId 为空记 system，委托既有 invokeStream。
     *
     * @param agentNum   目标 Agent 业务编号（已由过滤器校验与秘钥归属一致）
     * @param input      用户输入
     * @param sessionNum 会话业务编号，可空（下游新建）
     * @param operatorId 调用方操作人，可空
     * @return Event 流（adapter 接到 SSE）
     */
    public Flux<Event> invoke(String agentNum, String input, String sessionNum, String operatorId) {
        return agentRunnerService.runAgent(agentNum, input, sessionNum, resolveOperator(operatorId));
    }

    /**
     * 对外创建会话；委托既有 createSession。
     *
     * @param agentNum   目标 Agent 业务编号
     * @param skillHint  Skill 提示，可空
     * @param title      会话标题，可空
     * @param operatorId 调用方操作人，可空
     * @return 新建会话 DTO
     */
    public SessionDTO createSession(String agentNum, String skillHint, String title, String operatorId) {
        return sessionCommandService.createSession(agentNum, skillHint, title, resolveOperator(operatorId), "API");
    }

    /**
     * 对外会话分页列表；委托既有 pageList（按 agentNum 维度，不再按操作人过滤）。
     *
     * @param query 列表查询（agentNum 必填 + 分页）
     * @return 分页结果
     */
    public PageVO<SessionListVO> listSessions(SessionListQuery query) {
        return sessionQueryService.pageList(query, null);
    }

    /**
     * 对外会话详情（含消息链）；委托既有 detail（仅校验存在性）。
     *
     * @param sessionNum 会话业务编号
     * @return 会话详情 VO
     */
    public SessionDetailVO sessionDetail(String sessionNum) {
        return sessionQueryService.detail(sessionNum);
    }

    /** operatorId 为空 → system。 */
    private String resolveOperator(String operatorId) {
        return (operatorId == null || operatorId.isBlank()) ? SYSTEM_OPERATOR : operatorId;
    }
}
