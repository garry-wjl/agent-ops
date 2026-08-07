package ink.garry.rd.agent.ws.adapter.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * SSE 流响应格式化工具：在序列化为 JSON 后，对 {@code format_json} 工具的结果做特殊处理。
 * <p>
 * AgentScope 框架的 {@code ToolResultBlock.output} 固定为 {@code List<ContentBlock>},
 * ContentBlock 子类只有 TextBlock/ImageBlock 等类型，无法原生输出 {@code type:"object"}，
 * 故在此处对已序列化的 JSON 树进行后置替换：
 * <ul>
 *   <li>查找 {@code "name":"format_json"} 的 tool_result 块</li>
 *   <li>将其 output 中的 {@code {"type":"text","text":"{...}"}} 替换为
 *       {@code {"type":"object","value":<parsed JSON>}}</li>
 * </ul>
 * <p>
 * 无状态单例，线程安全。
 */
@Slf4j
public class SseEventTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FORMAT_JSON_TOOL_NAME = "format_json";

    private SseEventTransformer() {
        // 工具类，无实例
    }

    /**
     * 遍历 Event 的 JSON 树，找到 {@code format_json} 工具的结果进行变换。
     *
     * @param root 已序列化为 JsonNode 的 Event 对象（可变操作，直接修改树）
     */
    public static void transformFormatJsonResults(JsonNode root) {
        if (root == null) {
            return;
        }
        // 顶层结构：{"type":"...","message":{...},"isLast":...,"messageId":"..."}
        JsonNode messageNode = root.get("message");
        if (messageNode == null || !messageNode.isObject()) {
            return;
        }
        // message.content 是 ContentBlock 数组
        JsonNode content = messageNode.get("content");
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            if (!block.isObject()) {
                continue;
            }
            // 查找 tool_result 类型的内容块
            String blockType = block.get("type") != null ? block.get("type").asText() : "";
            if (!"tool_result".equals(blockType)) {
                continue;
            }
            // 检查工具名是否为 format_json
            JsonNode nameNode = block.get("name");
            if (nameNode == null || !FORMAT_JSON_TOOL_NAME.equals(nameNode.asText())) {
                continue;
            }
            // 找到 output 数组，替换其中的 TextBlock
            JsonNode output = block.get("output");
            if (output == null || !output.isArray()) {
                continue;
            }
            transformOutputArray((ArrayNode) output);
        }
    }

    /**
     * 替换 output 数组中的 TextBlock 为 ObjectBlock。
     * <p>
     * 原：{@code {"type":"text","text":"{...}"}}
     * 新：{@code {"type":"object","value":<parsed JSON>}}
     * <p>
     * 若解析失败（文本不是合法 JSON），则保留原样。
     */
    private static void transformOutputArray(ArrayNode outputArray) {
        for (int i = 0; i < outputArray.size(); i++) {
            JsonNode item = outputArray.get(i);
            if (!item.isObject()) {
                continue;
            }
            String itemType = item.get("type") != null ? item.get("type").asText() : "";
            if (!"text".equals(itemType)) {
                continue;
            }
            JsonNode textNode = item.get("text");
            if (textNode == null) {
                continue;
            }
            String text = textNode.asText();
            if (text == null || text.isBlank()) {
                continue;
            }

            // 尝试将文本解析为 JSON
            try {
                JsonNode parsedValue = MAPPER.readTree(text);
                // 创建 {"type":"object","value":<parsed JSON>}
                ObjectNode objectBlock = MAPPER.createObjectNode();
                objectBlock.put("type", "object");
                objectBlock.set("value", parsedValue);
                outputArray.set(i, objectBlock);
            } catch (Exception e) {
                // 解析失败，保留原 TextBlock
                log.debug("format_json 文本内容非标准 JSON，跳过变换: {}", e.getMessage());
            }
        }
    }
}