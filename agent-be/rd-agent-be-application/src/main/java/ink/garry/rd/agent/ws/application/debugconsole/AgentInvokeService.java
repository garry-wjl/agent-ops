package ink.garry.rd.agent.ws.application.debugconsole;

import ink.garry.rd.agent.ws.application.agentrunner.AgentRunnerService;
import ink.garry.rd.agent.ws.application.agentrunner.InvokeContentNormalizer;
import ink.garry.rd.agent.ws.application.agentrunner.NormalizedInvokeContent;
import ink.garry.rd.agent.ws.client.attachment.AttachmentRefParam;
import io.agentscope.core.agent.Event;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent 调用服务（流式）。
 * <p>
 * 派发到对应的 AgentRunner，返回 PlatformEvent 流；adapter 层负责接到 SSE 上。
 */
@Service
@RequiredArgsConstructor
public class AgentInvokeService {

    @Resource
    private AgentRunnerService agentRunnerService;

    @Resource
    private InvokeContentNormalizer invokeContentNormalizer;

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
        return invokeStream(agentNum, input, null, sessionNum, operatorId, null, null);
    }

    /**
     * 版本化调试：按目标版本装配 Runner，返回 Event 流。
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
        return invokeStream(agentNum, input, null, sessionNum, operatorId, targetVersion, null);
    }

    /**
     * 版本化调试（带调用上下文）。
     */
    @Transactional
    public Flux<Event> invokeStream(String agentNum, String input, String sessionNum, String operatorId,
                                    String targetVersion, Map<String, Object> context) {
        return invokeStream(agentNum, input, null, sessionNum, operatorId, targetVersion, context);
    }

    /**
     * 版本化调试（支持附件）；归一化失败抛业务异常 → HTTP 4xx（SSE 前）。
     */
    @Transactional
    public Flux<Event> invokeStream(String agentNum, Object input, List<AttachmentRefParam> attachments,
                                    String sessionNum, String operatorId, String targetVersion,
                                    Map<String, Object> context) {
        NormalizedInvokeContent content = invokeContentNormalizer.normalize(input, attachments);
        return agentRunnerService.runAgent(
                agentNum, content, sessionNum, operatorId, targetVersion, "DEBUG_CONSOLE", context);
    }
}
