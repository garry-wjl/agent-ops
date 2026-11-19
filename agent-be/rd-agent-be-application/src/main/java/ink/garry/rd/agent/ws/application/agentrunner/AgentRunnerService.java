package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.agent.StructuredOutputRules;
import ink.garry.rd.agent.ws.application.agentrunner.factory.AgentRunnerFactory;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunEventStore;
import ink.garry.rd.agent.ws.application.agentrunner.run.AgentRunStatus;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.agentrunner.StructuredInvokeVO;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.application.agentrunner.harness.HarnessAgentReleaser;
import ink.garry.rd.agent.ws.application.agentrunner.harness.HarnessUserMemorySync;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.RequestUserInputTool;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.UserInputHitlService;
import ink.garry.rd.agent.ws.application.attachment.command.AttachmentCommandService;
import ink.garry.rd.agent.ws.application.common.prompt.SysPromptVariableSubstitutor;
import ink.garry.rd.agent.ws.application.debugconsole.SegmentAccumulator;
import ink.garry.rd.agent.ws.application.sandbox.lifecycle.SessionSandboxLifecycleService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRequiredEvent;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.domain.session.valueobject.TurnTokenUsage;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolResultMessageBuilder;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.sandbox.SandboxContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AgentRunnerService {

    @Resource
    private AgentRunnerFactory agentRunnerFactory;


    @Resource
    private SessionCommandService sessionCommandService;

    @Resource
    private io.agentscope.core.state.AgentStateStore agentStateStore;

    @Resource
    private AgentMsgFactory agentMsgFactory;

    @Resource
    private AttachmentCommandService attachmentCommandService;

    @Resource
    private HarnessUserMemorySync harnessUserMemorySync;

    @Resource
    private SessionSandboxLifecycleService sessionSandboxLifecycleService;

    @Resource
    private SandboxPoolService sandboxPoolService;

    @Resource
    private UserInputHitlService userInputHitlService;

    @Resource
    private AgentQueryService agentQueryService;

    @Resource
    private AgentRunEventStore eventStore;

    /** 结构化输出同步调用的最长等待，与流式 run 超时一致。 */
    @Value("${app.agent-run.run-timeout:16m}")
    private Duration runTimeout = Duration.ofMinutes(16);

    /** SSE/流式执行期间沙箱心跳间隔：刷新 lastActiveAt + 远程 renew。 */
    private static final Duration SANDBOX_HEARTBEAT_INTERVAL = Duration.ofSeconds(60);

    /**
     * 运行 Agent（生产/默认入口：当前在线版本）。
     * <p>
     * 供对外（API 秘钥）调用，故自动新建会话时来源标记为 {@code API}。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, null, "API", null);
    }

    /**
     * 运行 Agent（生产入口，带调用上下文）。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId,
                                Map<String, Object> context) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, null, "API", context);
    }

    /**
     * 运行 Agent（版本化调试入口）。
     * <p>
     * {@code targetVersion} 决定装配哪个版本快照：空→当前在线；{@code DRAFT}→草稿态；vX.Y.Z→指定发布/历史版。
     * 生产调用传 null（当前在线，行为不变）；调试台按所选版本传入。
     * <p>
     * 本重载供 web 调试台调用，故自动新建会话时来源标记为 {@code DEBUG_CONSOLE}。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId,
                                String targetVersion) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, targetVersion, "DEBUG_CONSOLE", null);
    }

    /**
     * 运行 Agent（版本化调试入口，带调用上下文）。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId,
                                String targetVersion, Map<String, Object> context) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, targetVersion, "DEBUG_CONSOLE", context);
    }

    /**
     * 运行 Agent（最底层实现，显式指定自动建会话的来源）。
     * <p>
     * {@code origin} 仅在 {@code sessionNum} 为空、需自动新建会话时生效，决定会话来源标签
     * （{@code API} / {@code DEBUG_CONSOLE}）。
     * <p>
     * {@code context} 非空时：新建会话写入默认上下文，已有会话则浅合并后落库；
     * 随后与内置变量合并，供系统提示词 {@code {{key}}} 替换。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId,
                                String targetVersion, String origin) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, targetVersion, origin, null);
    }

    /**
     * 运行 Agent（纯文本兼容入口，带上下文）。
     */
    public Flux<Event> runAgent(String agentNum, String input, String sessionNum, String operatorId,
                                String targetVersion, String origin, Map<String, Object> context) {
        return runAgent(agentNum, textOnly(input), sessionNum, operatorId, targetVersion, origin, context);
    }

    /**
     * 运行 Agent（多模态 / 附件入口）— 仅 AgentScope Event（评测等消费者）。
     */
    public Flux<Event> runAgent(String agentNum, NormalizedInvokeContent content, String sessionNum,
                                String operatorId, String targetVersion, String origin,
                                Map<String, Object> context) {
        return runAgentFrames(agentNum, content, sessionNum, operatorId, targetVersion, origin, context)
                .filter(AgentInvokeFrame::isAgentEvent)
                .map(AgentInvokeFrame::getAgentEvent);
    }

    /**
     * 运行 Agent 并合并会话沙箱状态帧（调试台 / Open SSE）。
     * <p>
     * 订阅 {@link SessionSandboxLifecycleService}：用户路径首次用沙箱时推送
     * {@code event: Sandbox.Status}；纯对话不阻塞、预热未订阅则不打扰 UI。
     */
    public Flux<AgentInvokeFrame> runAgentFrames(String agentNum, NormalizedInvokeContent content,
                                                   String sessionNum, String operatorId,
                                                   String targetVersion, String origin,
                                                   Map<String, Object> context) {
        return runAgentFrames(agentNum, content, sessionNum, operatorId, targetVersion, origin, context, null);
    }

    /**
     * 结构化输出：等本轮结束，返回符合 Schema 的 JSON。
     * <p>
     * 工具过程仍写入会话；响应里只有最终 JSON 和 Token。
     *
     * @param agentNum      Agent 业务编号
     * @param content       已归一化的输入
     * @param sessionNum    会话编号，可空
     * @param operatorId    操作人
     * @param targetVersion 目标版本，可空
     * @param origin        新建会话来源
     * @param context       调用上下文
     * @return 同步结果
     */
    public StructuredInvokeVO runStructured(String agentNum, NormalizedInvokeContent content,
                                             String sessionNum, String operatorId,
                                             String targetVersion, String origin,
                                             Map<String, Object> context) {
        String runId = "RUN" + IdUtil.getSnowflakeNextIdStr();
        AtomicReference<StructuredInvokeVO> sink = new AtomicReference<>();
        try {
            runAgentFrames(agentNum, content, sessionNum, operatorId, targetVersion, origin, context,
                    runId, sink)
                    .timeout(runTimeout)
                    .blockLast();
        } catch (RuntimeException ex) {
            BusinessException business = findCause(ex, BusinessException.class);
            if (business != null) {
                throw business;
            }
            if (findCause(ex, TimeoutException.class) != null) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "结构化输出超过运行时限");
            }
            throw ex;
        }
        StructuredInvokeVO vo = sink.get();
        if (vo == null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), StructuredOutputRules.SCHEMA_INVALID);
        }
        return vo;
    }

    /**
     * @param runId 由 {@code AgentRunCoordinator} 分配；HITL 事件携带，便于 respond 关联
     */
    public Flux<AgentInvokeFrame> runAgentFrames(String agentNum, NormalizedInvokeContent content,
                                                   String sessionNum, String operatorId,
                                                   String targetVersion, String origin,
                                                   Map<String, Object> context, String runId) {
        return runAgentFrames(agentNum, content, sessionNum, operatorId, targetVersion, origin,
                context, runId, null);
    }

    /**
     * @param sink 非空表示同步结构化输出；流式入口必须传 null
     */
    private Flux<AgentInvokeFrame> runAgentFrames(String agentNum, NormalizedInvokeContent content,
                                                   String sessionNum, String operatorId,
                                                   String targetVersion, String origin,
                                                   Map<String, Object> context, String runId,
                                                   AtomicReference<StructuredInvokeVO> sink) {
        Assert.notNull(content, "invoke content 不能为空");
        AgentDTO loadedAgent = agentQueryService.loadAgentForDebug(agentNum, targetVersion);
        boolean structured = StructuredReply.enabled(loadedAgent);
        if (structured && sink == null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), StructuredOutputRules.STREAM_REJECTED);
        }
        if (!structured && sink != null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), StructuredOutputRules.JSON_REJECTED);
        }
        JsonNode schema = structured ? StructuredReply.schemaNode(loadedAgent) : null;
        //1. 确定会话(沙箱工具按 sessionNum 绑定复用容器,故须先于 build 确定)
        Map<String, Object> sessionContext;
        if (StrUtil.isBlank(sessionNum)) {
            SessionDTO sessionDTO = sessionCommandService.createSession(
                    agentNum, "", "", operatorId, origin, context);
            sessionNum = sessionDTO.getNum();
            sessionContext = sessionDTO.getInvokeContext() != null
                    ? sessionDTO.getInvokeContext()
                    : Map.of();
        } else {
            sessionContext = sessionCommandService.mergeInvokeContext(sessionNum, context, operatorId);
        }

        // 合并变量：内置 → 会话默认（已含本轮浅合并）（后写覆盖）
        SessionDTO sessionMeta = sessionCommandService.getSession(sessionNum);
        String agentVersionNum = sessionMeta != null ? sessionMeta.getAgentVersionNum() : "";
        // 调试指定 targetVersion 时，内置 AGENT_VERSION_NUM 优先展示目标版本语义
        if (StrUtil.isNotBlank(targetVersion)) {
            agentVersionNum = targetVersion;
        }
        String workspaceNum = WorkspaceContextHolder.currentWorkspaceNum();
        // 调试台：common 上传未登记；Open：uploadAttachment 已登记。此处统一 ensure。
        if (content.hasAttachments()) {
            attachmentCommandService.ensureRegisteredForInvoke(
                    workspaceNum, agentNum, content, operatorId);
        }
        Map<String, String> vars = SysPromptVariableSubstitutor.merge(
                SysPromptVariableSubstitutor.builtinVars(
                        sessionNum, agentNum, agentVersionNum, workspaceNum, operatorId),
                sessionContext);

        final String trackedRunId;
        final boolean trackRun = sink != null;
        if (trackRun) {
            trackedRunId = StrUtil.blankToDefault(runId, "RUN" + IdUtil.getSnowflakeNextIdStr());
            eventStore.findRunningRunId(sessionNum).ifPresent(busy -> {
                throw new BusinessException(BizCode.SESSION_BUSY.getCode(),
                        BizCode.SESSION_BUSY.getMessage() + " runId=" + busy);
            });
            eventStore.createRun(trackedRunId, sessionNum, agentNum);
        } else {
            trackedRunId = runId;
        }
        try {
            //2. 创建 Agent(注入 sessionNum 以绑定会话级沙箱；按 targetVersion 装配目标版本快照；有附件时注册 read_attachment)
            Agent agent = agentRunnerFactory.build(
                    agentNum, sessionNum, targetVersion, vars, content.hasAttachments());

            //3. 添加用户消息（MULTIMODAL 存 JSON；纯文本保持 TEXT）
            AgentMsgFactory.PersistPayload persist = agentMsgFactory.toPersistPayload(content);
            sessionCommandService.appendUserMessage(
                    sessionNum, persist.contentText(), persist.inputType(), sessionNum, trackedRunId, operatorId);
            return startAgentFrames(agent, agentNum, sessionNum, operatorId, targetVersion, workspaceNum,
                    content, trackedRunId, schema, sink, trackRun);
        } catch (RuntimeException ex) {
            if (trackRun) {
                eventStore.complete(trackedRunId, AgentRunStatus.FAILED);
            }
            throw ex;
        }
    }

    /**
     * 装配完成后开始出帧。结构化调用在结束时登记运行结果。
     */
    private Flux<AgentInvokeFrame> startAgentFrames(Agent agent, String agentNum, String sessionNum,
                                                     String operatorId, String targetVersion,
                                                     String workspaceNum, NormalizedInvokeContent content,
                                                     String runId, JsonNode schema,
                                                     AtomicReference<StructuredInvokeVO> sink,
                                                     boolean trackRun) {
        Msg msg = agentMsgFactory.build(content, workspaceNum);
        String finalSessionNum = sessionNum;
        String finalRunId = runId;
        // 累积器:订阅 PostReasoning / PostActing 的 isLast=true 帧,把 thinking / text / tool_use /
        // tool_result 按到达顺序收成 AssistantSegment 列表,最终随 assistant message 一并持久化。
        // 这样历史消息 FE 直接渲染,与本轮流式视觉一致(见技术方案 2026-05-28)。
        SegmentAccumulator acc = new SegmentAccumulator();
        // 汇总本轮 Token：累加各 REASONING 末帧 usage，并在 AGENT_RESULT 上回填，
        // 供开放 Event SSE / A2UI / 调试台统一读取 message.usage。
        TokenUsageAccumulator usageAcc = new TokenUsageAccumulator();

        // 跨 Event 状态标记：当前是否处于 <mm:think>...</mm:think>（MiniMax）类型标签内。
        // AgentScope 2.0 SDK 的 formatter 只认标准 reasoning_content 字段来输出 ThinkingBlock，
        // 而 MiniMax 把推理内容以 XML 标签包裹放进 text 字段，被误转为 TextBlock。
        // transformEvent 拦截 REASONING 事件，将标签内文本转为 ThinkingBlock，上游无感。
        AtomicBoolean inThinkTag = new AtomicBoolean(false);

        RuntimeContext.Builder runtimeBuilder = RuntimeContext.builder()
                .userId(StrUtil.blankToDefault(operatorId, "anonymous"))
                .sessionId(finalSessionNum);
        // 方案 B：注入 user-managed SandboxContext，避免 Harness 每轮 shutdown 清 /workspace
        if (agent instanceof HarnessAgent) {
            SandboxContext userManaged = agentRunnerFactory.buildUserManagedSandboxContext(
                    agentNum, finalSessionNum, targetVersion);
            if (userManaged != null) {
                runtimeBuilder.put(SandboxContext.class, userManaged);
            }
        }
        RuntimeContext runtimeContext = runtimeBuilder.build();

        Flux<AgentInvokeFrame> agentFrames = streamWithUserInputHitl(
                agent, msg, runtimeContext, finalSessionNum, finalRunId, operatorId,
                agentNum, targetVersion, acc, usageAcc, inThinkTag, new AtomicBoolean(false),
                schema, sink);

        AtomicReference<Disposable> heartbeat = new AtomicReference<>();
        // publish 避免 merge + takeUntilOther 对冷流双重订阅导致 Agent 跑两遍
        return agentFrames
                .doOnSubscribe(s -> startSandboxHeartbeat(finalSessionNum, heartbeat))
                .doFinally(sig -> {
                    stopSandboxHeartbeat(heartbeat);
                    // 流结束（成功 / 失败 / 客户端断开）后关闭本次 HarnessAgent，
                    // 否则 GracefulShutdownManager 会把它留到进程重启。
                    HarnessAgentReleaser.releaseAsync(agent);
                    if (trackRun) {
                        AgentRunStatus status = sig == SignalType.ON_COMPLETE && sink.get() != null
                                ? AgentRunStatus.DONE
                                : AgentRunStatus.FAILED;
                        eventStore.complete(finalRunId, status);
                    }
                })
                .publish(shared -> Flux.merge(
                        sessionSandboxLifecycleService.subscribe(finalSessionNum)
                                .map(AgentInvokeFrame::sandboxStatus)
                                .takeUntilOther(shared.ignoreElements()),
                        shared));
    }

    /**
     * 流式执行；遇 {@code TOOL_SUSPENDED}+{@code request_user_input} 时发
     * {@code user_input.required}，等待 respond 后以 ToolResult 续跑（方案 A：同连接不结束）。
     */
    private Flux<AgentInvokeFrame> streamWithUserInputHitl(
            Agent agent, Msg input, RuntimeContext runtimeContext,
            String sessionNum, String runId, String operatorId,
            String agentNum, String targetVersion,
            SegmentAccumulator acc, TokenUsageAccumulator usageAcc, AtomicBoolean inThinkTag,
            AtomicBoolean assistantPersisted, JsonNode schema,
            AtomicReference<StructuredInvokeVO> sink) {

        if (schema != null && !(agent instanceof HarnessAgent)) {
            return Flux.error(new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "结构化输出仅支持配置模式 Agent"));
        }
        Flux<Event> eventFlux = agent instanceof HarnessAgent harnessAgent
                ? (schema == null
                    ? harnessAgent.stream(input, runtimeContext)
                    : harnessAgent.stream(List.of(input), StreamOptions.defaults(), schema, runtimeContext))
                : agent.stream(input);

        Flux<AgentInvokeFrame> frames = eventFlux
                .map(event -> transformEvent(event, inThinkTag))
                .doOnNext(acc::accept)
                .doOnNext(usageAcc::accept)
                .map(usageAcc::ensureOnAgentResult)
                .concatMap(event -> {
                    if (!isTerminalAgentResult(event)) {
                        return Flux.just(AgentInvokeFrame.agent(event));
                    }
                    if (isUserFormSuspended(event)) {
                        return handleUserFormSuspend(
                                agent, event, runtimeContext, sessionNum, runId, operatorId,
                                agentNum, targetVersion, acc, usageAcc, inThinkTag, assistantPersisted);
                    }
                    TurnTokenUsage turnUsage = TurnTokenUsageMapper.from(
                            event.getMessage() == null ? null : event.getMessage().getChatUsage());
                    return Mono.fromRunnable(() -> {
                                if (schema != null) {
                                    Map<String, Object> json = StructuredReply.requireObject(event.getMessage());
                                    String jsonText = StructuredReply.toJsonText(json);
                                    List<AssistantSegment> segments = StructuredReply.segmentsWithJson(
                                            acc.getSegments(), jsonText);
                                    sessionCommandService.appendAssistantMessage(
                                            sessionNum,
                                            jsonText,
                                            acc.toStepChain(),
                                            segments,
                                            null,
                                            sessionNum,
                                            runId,
                                            turnUsage,
                                            operatorId);
                                    if (sink != null) {
                                        sink.set(StructuredInvokeVO.builder()
                                                .sessionNum(sessionNum)
                                                .runId(runId)
                                                .json(json)
                                                .usage(toUsage(turnUsage))
                                                .build());
                                    }
                                } else {
                                    sessionCommandService.appendAssistantMessage(
                                            sessionNum,
                                            acc.toContentText(),
                                            acc.toStepChain(),
                                            acc.getSegments(),
                                            acc.toContentBlocksJson(),
                                            sessionNum,
                                            runId,
                                            turnUsage,
                                            operatorId);
                                }
                                assistantPersisted.set(true);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .thenMany(Flux.just(AgentInvokeFrame.agent(event)))
                            .doOnComplete(() -> harnessUserMemorySync.capture(
                                    agentNum, targetVersion, sessionNum, operatorId));
                })
                .takeUntil(frame -> frame.isAgentEvent()
                        && isTerminalAgentResult(frame.getAgentEvent())
                        && !isUserFormSuspended(frame.getAgentEvent()));
        if (schema != null) {
            return frames.concatWith(Mono.defer(() -> assistantPersisted.get()
                            ? Mono.empty()
                            : Mono.error(new BusinessException(
                                    BizCode.INVALID_PARAM.getCode(), StructuredOutputRules.SCHEMA_INVALID)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .thenMany(Flux.empty()));
        }
        return frames.concatWith(whenStreamEndedWithoutResult(
                        assistantPersisted,
                        acc,
                        () -> {
                            sessionCommandService.appendAssistantMessage(
                                    sessionNum,
                                    acc.toContentText(),
                                    acc.toStepChain(),
                                    acc.getSegments(),
                                    acc.toContentBlocksJson(),
                                    sessionNum,
                                    runId,
                                    TurnTokenUsageMapper.from(usageAcc.snapshot()),
                                    operatorId);
                            harnessUserMemorySync.capture(
                                    agentNum, targetVersion, sessionNum, operatorId);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.empty()));
    }

    private static StructuredInvokeVO.Usage toUsage(TurnTokenUsage usage) {
        if (usage == null) {
            return null;
        }
        return StructuredInvokeVO.Usage.builder()
                .inputTokens(usage.getInputTokens())
                .outputTokens(usage.getOutputTokens())
                .cachedTokens(usage.getCachedTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 流正常结束但没有 {@code AGENT_RESULT} 时：已有累积回复就补写助手消息，否则让本轮失败收尾。
     *
     * @param assistantPersisted 本轮是否已经写入助手消息
     * @param acc                流结束时再读的段累积器
     * @param persistReply       补写助手消息
     * @return 空完成，或「没有产生回复」的错误
     */
    static Mono<Void> whenStreamEndedWithoutResult(
            AtomicBoolean assistantPersisted,
            SegmentAccumulator acc,
            Runnable persistReply) {
        return Mono.defer(() -> {
            if (assistantPersisted.get()) {
                return Mono.empty();
            }
            if (acc.getSegments().isEmpty()) {
                return Mono.error(new BusinessException(
                        BizCode.SSE_INTERRUPTED.getCode(),
                        "Agent 结束时没有产生回复"));
            }
            return Mono.fromRunnable(() -> {
                persistReply.run();
                assistantPersisted.set(true);
            });
        });
    }

    private Flux<AgentInvokeFrame> handleUserFormSuspend(
            Agent agent, Event suspendEvent, RuntimeContext runtimeContext,
            String sessionNum, String runId, String operatorId,
            String agentNum, String targetVersion,
            SegmentAccumulator acc, TokenUsageAccumulator usageAcc, AtomicBoolean inThinkTag,
            AtomicBoolean assistantPersisted) {

        ToolUseBlock toolCall = extractRequestUserInputTool(suspendEvent.getMessage());
        if (toolCall == null) {
            log.warn("[user-input-hitl] TOOL_SUSPENDED without request_user_input, treat as finish");
            return Flux.just(AgentInvokeFrame.agent(suspendEvent));
        }
        Map<String, Object> form = toolCall.getInput() == null
                ? Map.of()
                : new HashMap<>(toolCall.getInput());
        String replyId = StrUtil.blankToDefault(
                suspendEvent.getMessage() != null ? suspendEvent.getMessage().getId() : null,
                UUID.randomUUID().toString().replace("-", ""));
        UserInputHitlService.Pending pending = userInputHitlService.register(
                sessionNum, runId, replyId, toolCall, form);
        UserInputRequiredEvent required = UserInputRequiredEvent.builder()
                .runId(runId)
                .sessionId(sessionNum)
                .replyId(replyId)
                .requestId(pending.getRequestId())
                .toolCallId(toolCall.getId())
                .toolName(RequestUserInputTool.NAME)
                .form(form)
                .build();

        return Flux.concat(
                Flux.just(AgentInvokeFrame.agent(suspendEvent)),
                Flux.just(AgentInvokeFrame.userInputRequired(required)),
                userInputHitlService.awaitAnswer(pending.getRequestId())
                        .flatMapMany(answer -> {
                            Map<String, Object> accepted = Map.of(
                                    "runId", StrUtil.blankToDefault(runId, ""),
                                    "requestId", pending.getRequestId(),
                                    "status", "cancel".equals(answer.getAction())
                                            ? "cancelled" : "submitted");
                            ToolResultBlock result = UserInputHitlService.toToolResult(pending, answer);
                            String agentName = agent.getName() != null ? agent.getName() : "agent";
                            Msg resumeMsg = ToolResultMessageBuilder.buildToolResultMsg(
                                    result, toolCall, agentName);
                            acc.appendUserForm(
                                    pending.getForm(),
                                    pending.getRequestId(),
                                    "cancel".equals(answer.getAction()) ? "cancelled" : "submitted",
                                    answer.getValues());
                            return Flux.concat(
                                    Flux.just(AgentInvokeFrame.userInputAccepted(accepted)),
                                    streamWithUserInputHitl(
                                            agent, resumeMsg, runtimeContext, sessionNum, runId,
                                            operatorId, agentNum, targetVersion,
                                            acc, usageAcc, inThinkTag, assistantPersisted,
                                            null, null));
                        }));
    }

    static boolean isTerminalAgentResult(Event event) {
        return event != null
                && event.isLast()
                && EventType.AGENT_RESULT.equals(event.getType());
    }

    static boolean isUserFormSuspended(Event event) {
        if (!isTerminalAgentResult(event) || event.getMessage() == null) {
            return false;
        }
        if (event.getMessage().getGenerateReason() != GenerateReason.TOOL_SUSPENDED) {
            return false;
        }
        return extractRequestUserInputTool(event.getMessage()) != null;
    }

    static ToolUseBlock extractRequestUserInputTool(Msg msg) {
        if (msg == null) {
            return null;
        }
        List<ToolUseBlock> uses = msg.getContentBlocks(ToolUseBlock.class);
        if (uses == null || uses.isEmpty()) {
            return null;
        }
        for (ToolUseBlock use : uses) {
            if (use != null && RequestUserInputTool.NAME.equals(use.getName())) {
                return use;
            }
        }
        return null;
    }

    /**
     * SSE 全程保活：立即 markSessionActive，并按间隔续期，避免空闲回收 / ACS timeout 误杀沙箱。
     */
    private void startSandboxHeartbeat(String sessionNum, AtomicReference<Disposable> holder) {
        try {
            sandboxPoolService.markSessionActive(sessionNum);
        } catch (Exception e) {
            log.debug("[sandbox-heartbeat] initial mark failed sessionNum={}: {}",
                    sessionNum, e.getMessage());
        }
        Disposable previous = holder.getAndSet(Flux.interval(SANDBOX_HEARTBEAT_INTERVAL)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        tick -> {
                            try {
                                sandboxPoolService.markSessionActive(sessionNum);
                            } catch (Exception e) {
                                log.warn("[sandbox-heartbeat] mark failed sessionNum={}: {}",
                                        sessionNum, e.getMessage());
                            }
                        },
                        err -> log.warn("[sandbox-heartbeat] stream error sessionNum={}: {}",
                                sessionNum, err.toString())));
        if (previous != null) {
            previous.dispose();
        }
    }

    private static void stopSandboxHeartbeat(AtomicReference<Disposable> holder) {
        Disposable d = holder.getAndSet(null);
        if (d != null) {
            d.dispose();
        }
    }

    private static NormalizedInvokeContent textOnly(String input) {
        return NormalizedInvokeContent.builder()
                .text(input)
                .attachments(List.of())
                .build();
    }

    /**
     * 拦截 REASONING 事件，将 TextBlock 中 think 标签包裹的内容转为 ThinkingBlock。
     * <p>
     * <b>为什么要做：</b>AgentScope 2.0 的 OpenAI formatter 只按标准协议
     * {@code reasoning_content} 字段输出 {@link ThinkingBlock}。而 MiniMax 等模型将推理内容
     * 以 XML 标签（如 {@code <mm:think>...</mm:think>}）包裹后混入 {@code content} 字段，
     * 导致 AgentScope 将其当作普通 {@link TextBlock} 透出，前端/累积器无法识别为深度思考。
     * <p>
     * <b>处理规则：</b>仅在 REASONING 事件中将 {@code <mm:think>} / {@code </mm:think>}
     * 标签内的文本从 TextBlock 拆分为 ThinkingBlock。标签外的文本保留为 TextBlock。
     * 开闭标签可能跨 Event（chunk 帧），故使用 {@code inThinkTag} 标记跨 Event 状态。
     * <p>
     * 其他事件类型原样透出。
     *
     * @param event     原始事件
     * @param inThinkTag 跨 Event 状态标记：当前是否处于 think 标签内
     * @return 转换后的事件
     */
    static Event transformEvent(Event event, AtomicBoolean inThinkTag) {
        if (event == null || event.getType() != EventType.REASONING) {
            return event;
        }
        Msg msg = event.getMessage();
        if (msg == null || CollectionUtil.isEmpty(msg.getContent())) {
            return event;
        }
        List<ContentBlock> transformed = new ArrayList<>(msg.getContent().size());
        for (ContentBlock b : msg.getContent()) {
            if (!(b instanceof TextBlock tx)) {
                // 非 TextBlock（ThinkingBlock / ToolUseBlock 等）原样保留
                transformed.add(b);
                continue;
            }
            String text = tx.getText();
            if (StrUtil.isEmpty(text)) continue;

            if (inThinkTag.get()) {
                // 当前在 think 标签内：查找闭合标签
                int closeIdx = text.indexOf("</mm:think>");
                if (closeIdx >= 0) {
                    String thinkPart = text.substring(0, closeIdx);
                    if (!thinkPart.isEmpty()) {
                        transformed.add(ThinkingBlock.builder().thinking(thinkPart).build());
                    }
                    String after = text.substring(closeIdx + "</mm:think>".length());
                    if (!after.isEmpty()) {
                        transformed.add(TextBlock.builder().text(after).build());
                    }
                    inThinkTag.set(false);
                } else {
                    // 非闭合：整段是 thinking 内容
                    transformed.add(ThinkingBlock.builder().thinking(text).build());
                }
            } else {
                // 不在 think 标签内：查找开始标签
                int openIdx = text.indexOf("<mm:think>");
                if (openIdx >= 0) {
                    String before = text.substring(0, openIdx);
                    if (!before.isEmpty()) {
                        transformed.add(TextBlock.builder().text(before).build());
                    }
                    String afterOpen = text.substring(openIdx + "<mm:think>".length());
                    // 同一 text 内既有开始又有闭合标签
                    int closeIdx = afterOpen.indexOf("</mm:think>");
                    if (closeIdx >= 0) {
                        String thinkPart = afterOpen.substring(0, closeIdx);
                        if (!thinkPart.isEmpty()) {
                            transformed.add(ThinkingBlock.builder().thinking(thinkPart).build());
                        }
                        String after = afterOpen.substring(closeIdx + "</mm:think>".length());
                        if (!after.isEmpty()) {
                            transformed.add(TextBlock.builder().text(after).build());
                        }
                    } else {
                        // 仅开始无闭合：剩余内容进入 thinking，标记状态
                        if (!afterOpen.isEmpty()) {
                            transformed.add(ThinkingBlock.builder().thinking(afterOpen).build());
                        }
                        inThinkTag.set(true);
                    }
                } else {
                    // 不含 think 标签：原样保留
                    transformed.add(b);
                }
            }
        }
        // 构造新的 Msg 和事件，保留 id/name/role/metadata/timestamp/usage 不变
        Msg newMsg = Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(transformed)
                .metadata(msg.getMetadata())
                .timestamp(msg.getTimestamp())
                .usage(msg.getChatUsage())
                .build();
        return new Event(event.getType(), newMsg, event.isLast(), event.getSource());
    }
}