package ink.garry.rd.agent.ws.application.debugconsole;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.agentrunner.factory.AgentRunnerFactory;
//import ink.garry.rd.agent.ws.application.agent.strategy.AgentRunnerRegistry;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.domain.agent.factory.AgentFactory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentGateway;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentVersionGateway;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Agent 调用服务（流式）。
 * <p>
 * 派发到对应的 AgentRunner，返回 PlatformEvent 流；adapter 层负责接到 SSE 上。
 * <p>
 * v2.0：按 creationMode 分支装配 InvokeContext：
 * <ul>
 *   <li>{@code CONFIG}：加载当前在线版本 + snapshot；skill_hint / sessionNum 正常透传</li>
 *   <li>{@code A2A}：跳过 version 校验（A2A 不参与版本化）；填 a2aSource；skill_hint 由 Runner 忽略</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AgentInvokeService {

    @Resource
    private AgentRunnerService agentRunnerService;

    /**
     * 测试用，按 creationMode 派发到对应 Runner，返回 Event 流。
     *
     * @param agentNum   Agent 业务编号
     * @param input      调用输入
     * @param sessionNum 调用会话编号
     * @param operatorId 操作人 userId
     * @return Event 流
     */
    @Transactional
    public Flux<Event> invokeStream(String agentNum, String input, String sessionNum, String operatorId) {
        return invokeStream(agentNum, input, sessionNum, operatorId, null);
    }

    /**
     * 版本化调试：按目标版本装配 Runner，返回 Event 流。
     * <p>
     * {@code targetVersion} 语义见 {@code DebugInvokeRequest.targetVersion}：
     * 空→当前在线；{@code DRAFT}→草稿态（发布前验证）；vX.Y.Z→指定发布/历史版本。
     *
     * @param agentNum      Agent 业务编号
     * @param input         调用输入
     * @param sessionNum    调用会话编号
     * @param operatorId    操作人 userId
     * @param targetVersion 目标版本（空 / DRAFT / vX.Y.Z）
     * @return Event 流
     */
    @Transactional
    public Flux<Event> invokeStream(String agentNum, String input, String sessionNum, String operatorId,
                                    String targetVersion) {
        return agentRunnerService.runAgent(agentNum, input, sessionNum, operatorId, targetVersion);
    }

}