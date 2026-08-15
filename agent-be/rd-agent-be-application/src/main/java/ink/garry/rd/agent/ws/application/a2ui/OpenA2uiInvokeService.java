package ink.garry.rd.agent.ws.application.a2ui;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.agent.OpenAgentInvokeService;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiActionParam;
import ink.garry.rd.agent.ws.client.agent.OpenA2uiInvokeParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外 A2UI（v0.9.1）编排：复用 {@link OpenAgentInvokeService} 执行 Agent，
 * 经 {@link A2uiV091Encoder} 转为 A2UI SSE 消息；不改动既有 Event SSE 开放接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenA2uiInvokeService {

    private final OpenAgentInvokeService openAgentInvokeService;

    /**
     * A2UI 流式调用：用户文本 → Agent → A2UI envelopes。
     *
     * @param param 调用参数
     * @return A2UI 消息流（每元素一条完整 envelope Map）
     */
    public Flux<Map<String, Object>> invoke(OpenA2uiInvokeParam param) {
        A2uiV091Encoder encoder = newEncoder(param.getSurfaceId(), param.getCatalogId(), param.getSendDataModel());
        return streamA2ui(
                encoder,
                openAgentInvokeService.invoke(
                        param.getAgentNum(),
                        param.getInput(),
                        param.getSessionNum(),
                        param.getOperatorId(),
                        param.getContext()));
    }

    /**
     * A2UI action 回传：将 action（及可选 clientDataModel）转为 Agent 输入，再输出 A2UI 流。
     *
     * @param param action 请求
     * @return A2UI 消息流
     */
    public Flux<Map<String, Object>> action(OpenA2uiActionParam param) {
        OpenA2uiActionParam.A2uiActionPayload action = param.getAction();
        A2uiV091Encoder encoder = newEncoder(action.getSurfaceId(), null, true);
        String input = buildActionInput(param);
        Map<String, Object> mergedContext = mergeActionContext(param);
        return streamA2ui(
                encoder,
                openAgentInvokeService.invoke(
                        param.getAgentNum(),
                        input,
                        param.getSessionNum(),
                        param.getOperatorId(),
                        mergedContext));
    }

    private Flux<Map<String, Object>> streamA2ui(
            A2uiV091Encoder encoder,
            Flux<io.agentscope.core.agent.Event> events) {
        Flux<Map<String, Object>> prelude = Flux.fromIterable(encoder.bootstrap());
        Flux<Map<String, Object>> body = events.concatMapIterable(event -> {
            List<Map<String, Object>> messages = encoder.encode(event);
            return messages == null ? List.of() : messages;
        });
        return Flux.concat(prelude, body)
                .doOnError(e -> log.error("A2UI 流式编排失败 surfaceId={}", encoder.getSurfaceId(), e));
    }

    private static A2uiV091Encoder newEncoder(String surfaceId, String catalogId, Boolean sendDataModel) {
        boolean sync = sendDataModel == null || Boolean.TRUE.equals(sendDataModel);
        return new A2uiV091Encoder(surfaceId, catalogId, sync);
    }

    /**
     * 将 action 载荷格式化为 Agent 可读输入（结构化文本，便于模型理解交互意图）。
     */
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

    /**
     * 合并 invoke 上下文：请求 context ← action.context ← clientDataModel（后者覆盖同名键时以显式 context 优先）。
     */
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
        return merged.isEmpty() ? null : merged;
    }
}
