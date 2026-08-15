package ink.garry.rd.agent.ws.application.a2ui;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将 AgentScope {@link Event} 流转为 A2UI v0.9.1 server→client envelope。
 * <p>
 * <b>非线程安全</b>：每个 SSE 请求创建独立实例。策略：
 * <ol>
 *   <li>流开始：{@code createSurface} + 初始 {@code updateComponents}
 *       （root Column + 绑定 {@code /assistantText} 的 Text）+ 空 data model；</li>
 *   <li>REASONING 文本增量：{@code updateDataModel} 更新 {@code /assistantText}；</li>
 *   <li>流结束：不再强制 {@code deleteSurface}（保留 surface 供后续 action）。</li>
 * </ol>
 */
public class A2uiV091Encoder {

    private final String surfaceId;
    private final String catalogId;
    private final boolean sendDataModel;

    private boolean bootstrapped;
    private final StringBuilder assistantText = new StringBuilder();

    /**
     * @param surfaceId     surface 标识，空则用默认
     * @param catalogId     catalog URL，空则用官方 basic
     * @param sendDataModel createSurface.sendDataModel
     */
    public A2uiV091Encoder(String surfaceId, String catalogId, boolean sendDataModel) {
        this.surfaceId = StrUtil.blankToDefault(surfaceId, A2uiProtocol.DEFAULT_SURFACE_ID);
        this.catalogId = StrUtil.blankToDefault(catalogId, A2uiProtocol.DEFAULT_CATALOG_ID);
        this.sendDataModel = sendDataModel;
    }

    /** @return 当前 surfaceId */
    public String getSurfaceId() {
        return surfaceId;
    }

    /**
     * 生成流开头的 A2UI 消息（createSurface + 初始组件树 + 初始 data model）。
     *
     * @return 有序 envelope 列表
     */
    public List<Map<String, Object>> bootstrap() {
        if (bootstrapped) {
            return List.of();
        }
        bootstrapped = true;
        List<Map<String, Object>> out = new ArrayList<>(3);
        out.add(createSurfaceMessage());
        out.add(initialComponentsMessage());
        out.add(updateAssistantTextMessage(""));
        return out;
    }

    /**
     * 将单个 Agent Event 映射为零或多条 A2UI 消息。
     *
     * @param event AgentScope 事件，可空
     * @return A2UI envelopes；无可映射内容时为空列表
     */
    public List<Map<String, Object>> encode(Event event) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!bootstrapped) {
            out.addAll(bootstrap());
        }
        if (event == null) {
            return out;
        }
        // AGENT_RESULT 文案与末帧 REASONING 重复；此处只下发本轮 Token 用量（若有）。
        if (event.getType() == EventType.AGENT_RESULT) {
            ChatUsage usage = event.getMessage() != null ? event.getMessage().getChatUsage() : null;
            if (usage != null) {
                out.add(updateTokenUsageMessage(usage));
            }
            return out;
        }
        if (event.getType() == EventType.REASONING) {
            String frameText = extractTextBlocks(event.getMessage());
            if (StrUtil.isEmpty(frameText)) {
                return out;
            }
            if (event.isLast()) {
                // 完整帧：以本帧文本作为绝对快照，避免与 chunk 重复累加
                assistantText.setLength(0);
                assistantText.append(frameText);
            } else {
                String delta = toDelta(frameText);
                if (StrUtil.isEmpty(delta)) {
                    return out;
                }
                assistantText.append(delta);
            }
            out.add(updateAssistantTextMessage(assistantText.toString()));
        }
        return out;
    }

    private Map<String, Object> createSurfaceMessage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("surfaceId", surfaceId);
        body.put("catalogId", catalogId);
        body.put("sendDataModel", sendDataModel);
        return envelope("createSurface", body);
    }

    private Map<String, Object> initialComponentsMessage() {
        List<Map<String, Object>> components = new ArrayList<>(2);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", A2uiProtocol.ROOT_COMPONENT_ID);
        root.put("component", "Column");
        root.put("children", List.of(A2uiProtocol.ASSISTANT_TEXT_COMPONENT_ID));
        components.add(root);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("id", A2uiProtocol.ASSISTANT_TEXT_COMPONENT_ID);
        text.put("component", "Text");
        text.put("text", Map.of("path", A2uiProtocol.ASSISTANT_TEXT_PATH));
        text.put("variant", "body");
        components.add(text);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("surfaceId", surfaceId);
        body.put("components", components);
        return envelope("updateComponents", body);
    }

    private Map<String, Object> updateAssistantTextMessage(String value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("surfaceId", surfaceId);
        body.put("path", A2uiProtocol.ASSISTANT_TEXT_PATH);
        body.put("value", value == null ? "" : value);
        return envelope("updateDataModel", body);
    }

    private Map<String, Object> updateTokenUsageMessage(ChatUsage usage) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("inputTokens", usage.getInputTokens());
        value.put("outputTokens", usage.getOutputTokens());
        value.put("cachedTokens", usage.getCachedTokens());
        value.put("time", usage.getTime());
        value.put("totalTokens", usage.getTotalTokens());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("surfaceId", surfaceId);
        body.put("path", A2uiProtocol.TOKEN_USAGE_PATH);
        body.put("value", value);
        return envelope("updateDataModel", body);
    }

    private static Map<String, Object> envelope(String typeKey, Map<String, Object> body) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("version", A2uiProtocol.VERSION);
        msg.put(typeKey, body);
        return msg;
    }

    /**
     * 仅提取 {@link TextBlock} 可见文本；忽略 {@link ThinkingBlock}。
     */
    private static String extractTextBlocks(Msg message) {
        if (message == null || CollUtil.isEmpty(message.getContent())) {
            return null;
        }
        StringBuilder fromBlocks = new StringBuilder();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof TextBlock textBlock && StrUtil.isNotEmpty(textBlock.getText())) {
                fromBlocks.append(textBlock.getText());
            }
        }
        return fromBlocks.length() == 0 ? null : fromBlocks.toString();
    }

    /**
     * 将帧文本转为相对当前累积内容的增量：支持「纯 delta」与「累计全文」两种上游形态。
     */
    private String toDelta(String frameText) {
        String current = assistantText.toString();
        if (Objects.equals(current, frameText)) {
            return null;
        }
        if (frameText.startsWith(current)) {
            return frameText.substring(current.length());
        }
        // 非前缀：按纯 delta 追加
        return frameText;
    }

    /**
     * 将 A2UI envelope 序列化为 JSON 字符串（测试 / 调试辅助）。
     *
     * @param envelope A2UI 消息
     * @return JSON
     */
    public static String toJson(Map<String, Object> envelope) {
        return JSONObject.toJSONString(envelope);
    }
}
