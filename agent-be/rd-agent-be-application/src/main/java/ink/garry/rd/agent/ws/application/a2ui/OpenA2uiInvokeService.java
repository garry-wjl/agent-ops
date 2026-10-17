package ink.garry.rd.agent.ws.application.a2ui;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.application.agentrunner.AgentInvokeFrame;
import ink.garry.rd.agent.ws.application.agentrunner.hitl.UserInputHitlService;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiInvokeParam;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRespondParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外 A2UI（v0.9.1）编排：复用 {@link AgentRunnerService#runAgentFrames}（含表单 HITL），
 * 经 {@link A2uiV091Encoder} / {@link A2uiUserFormEncoder} 转为 A2UI SSE 消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenA2uiInvokeService {

    private final OpenAgentInvokeService openAgentInvokeService;
    private final UserInputHitlService userInputHitlService;

    /**
     * A2UI 流式调用：用户文本 → Agent（含 HITL）→ A2UI envelopes。
     */
    public Flux<Map<String, Object>> invoke(OpenA2uiInvokeParam param) {
        A2uiV091Encoder encoder = newEncoder(param.getSurfaceId(), param.getCatalogId(), param.getSendDataModel());
        Flux<AgentInvokeFrame> frames = openAgentInvokeService.invokeFrames(
                param.getAgentNum(),
                param.getInput(),
                null,
                param.getSessionNum(),
                param.getOperatorId(),
                param.getContext());
        return streamA2uiFrames(encoder, frames);
    }

    /**
     * A2UI action 回传。
     * <p>
     * {@code submit_user_input} / {@code cancel_user_input}：唤醒 HITL，不新开 Agent 轮次。
     * 其它 action：转为文本再 invoke（兼容旧行为）。
     */
    public Flux<Map<String, Object>> action(OpenA2uiActionParam param) {
        OpenA2uiActionParam.A2uiActionPayload action = param.getAction();
        String name = action == null ? null : action.getName();
        if ("submit_user_input".equals(name) || "cancel_user_input".equals(name)) {
            respondHitlFromAction(param);
            A2uiV091Encoder encoder = newEncoder(action.getSurfaceId(), null, true);
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("version", A2uiProtocol.VERSION);
            ack.put("surfaceId", action.getSurfaceId());
            ack.put("type", "updateDataModel");
            ack.put("path", "/hitlStatus");
            ack.put("value", "cancel_user_input".equals(name) ? "cancelled" : "submitted");
            return Flux.concat(Flux.fromIterable(encoder.bootstrap()), Flux.just(ack));
        }
        A2uiV091Encoder encoder = newEncoder(action.getSurfaceId(), null, true);
        String input = buildActionInput(param);
        Map<String, Object> mergedContext = mergeActionContext(param);
        Flux<AgentInvokeFrame> frames = openAgentInvokeService.invokeFrames(
                param.getAgentNum(),
                input,
                null,
                param.getSessionNum(),
                param.getOperatorId(),
                mergedContext);
        return streamA2uiFrames(encoder, frames);
    }

    @SuppressWarnings("unchecked")
    private void respondHitlFromAction(OpenA2uiActionParam param) {
        OpenA2uiActionParam.A2uiActionPayload action = param.getAction();
        Map<String, Object> ctx = action.getContext() == null ? Map.of() : action.getContext();
        Map<String, Object> model = param.getClientDataModel() == null
                ? Map.of()
                : param.getClientDataModel();
        UserInputRespondParam respond = new UserInputRespondParam();
        respond.setAction("cancel_user_input".equals(action.getName()) ? "cancel" : "submit");
        respond.setRequestId(firstStr(ctx.get("hitlRequestId"), model.get("hitlRequestId")));
        respond.setReplyId(firstStr(ctx.get("hitlReplyId"), model.get("hitlReplyId")));
        respond.setToolCallId(firstStr(ctx.get("hitlToolCallId"), model.get("hitlToolCallId")));
        respond.setRunId(firstStr(ctx.get("hitlRunId"), model.get("hitlRunId")));
        respond.setSessionNum(StrUtil.blankToDefault(
                param.getSessionNum(),
                firstStr(ctx.get("hitlSessionId"), model.get("hitlSessionId"))));
        Object values = model.get("values");
        if (values instanceof Map<?, ?> map) {
            respond.setValues((Map<String, Object>) map);
        } else if (ctx.get("values") instanceof Map<?, ?> map2) {
            respond.setValues((Map<String, Object>) map2);
        } else {
            respond.setValues(Map.of());
        }
        userInputHitlService.respond(respond);
    }

    private Flux<Map<String, Object>> streamA2uiFrames(
            A2uiV091Encoder encoder,
            Flux<AgentInvokeFrame> frames) {
        Flux<Map<String, Object>> prelude = Flux.fromIterable(encoder.bootstrap());
        Flux<Map<String, Object>> body = frames.concatMapIterable(frame -> {
            if (frame.isUserInputRequired()) {
                return A2uiUserFormEncoder.encodeForm(encoder.getSurfaceId(), frame.getUserInputRequired());
            }
            if (frame.isAgentEvent()) {
                List<Map<String, Object>> messages = encoder.encode(frame.getAgentEvent());
                return messages == null ? List.of() : messages;
            }
            return List.of();
        });
        return Flux.concat(prelude, body)
                .doOnError(e -> log.error("A2UI 流式编排失败 surfaceId={}", encoder.getSurfaceId(), e));
    }

    private static A2uiV091Encoder newEncoder(String surfaceId, String catalogId, Boolean sendDataModel) {
        boolean sync = sendDataModel == null || Boolean.TRUE.equals(sendDataModel);
        return new A2uiV091Encoder(surfaceId, catalogId, sync);
    }

    static String buildActionInput(OpenA2uiActionParam param) {
        OpenA2uiActionParam.A2uiActionPayload action = param.getAction();
        StringBuilder sb = new StringBuilder();
        sb.append("用户触发了 A2UI 界面动作，请据此继续处理。\n");
        sb.append("- action.name: ").append(action.getName()).append('\n');
        sb.append("- action.surfaceId: ").append(action.getSurfaceId()).append('\n');
        sb.append("- action.sourceComponentId: ").append(action.getSourceComponentId()).append('\n');
        sb.append("- action.timestamp: ").append(action.getTimestamp()).append('\n');
        sb.append("- action.context: ").append(JSON.toJSONString(action.getContext())).append('\n');
        if (param.getClientDataModel() != null && !param.getClientDataModel().isEmpty()) {
            sb.append("- clientDataModel: ").append(JSON.toJSONString(param.getClientDataModel())).append('\n');
        }
        return sb.toString();
    }

    static Map<String, Object> mergeActionContext(OpenA2uiActionParam param) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (param.getClientDataModel() != null) {
            merged.put("a2uiClientDataModel", param.getClientDataModel());
        }
        OpenA2uiActionParam.A2uiActionPayload action = param.getAction();
        if (action != null) {
            if (StrUtil.isNotBlank(action.getName())) {
                merged.put("a2uiActionName", action.getName());
            }
            if (StrUtil.isNotBlank(action.getSurfaceId())) {
                merged.put("a2uiSurfaceId", action.getSurfaceId());
            }
            if (StrUtil.isNotBlank(action.getSourceComponentId())) {
                merged.put("a2uiSourceComponentId", action.getSourceComponentId());
            }
            if (action.getContext() != null) {
                merged.put("a2uiActionContext", action.getContext());
            }
        }
        if (param.getContext() != null) {
            merged.putAll(param.getContext());
        }
        return merged;
    }

    private static String firstStr(Object a, Object b) {
        if (a != null && StrUtil.isNotBlank(String.valueOf(a))) {
            return String.valueOf(a);
        }
        if (b != null && StrUtil.isNotBlank(String.valueOf(b))) {
            return String.valueOf(b);
        }
        return null;
    }
}
