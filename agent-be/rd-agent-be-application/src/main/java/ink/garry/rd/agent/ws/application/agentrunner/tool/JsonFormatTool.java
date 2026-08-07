package ink.garry.rd.agent.ws.application.agentrunner.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * JSON 格式化工具：从任意文本中提取第一个合法的 JSON 对象或数组，以标准 JSON 格式返回。
 * <p>
 * 输入文本可能在 JSON 前后包含文字、注释或其他非 JSON 内容（例如 LLM 输出 "这是一段 JSON：{...}"），
 * 本工具会自动跳过前后非 JSON 字符，定位并解析第一个完整的 JSON 结构。
 * 支持嵌套对象、转义引号、Unicode 转义等边界情况。
 * <p>
 * 调用方传入 {@code key} 用于标识本次请求，响应中原样返回该 key，便于在流式处理中关联请求与结果。
 * <p>
 * 无状态单例，所有 Agent 可安全复用。
 */
@Slf4j
public class JsonFormatTool {

    /**
     * 提取文本中的第一个 JSON，格式化后返回。调用方传入 key 用于标识请求，响应中原样返回该 key。
     * <p>
     * 返回格式为一个 JSON 对象：{@code {"key":"xxx","json":<格式化后的 JSON>}}。
     *
     * @param key 请求标识，响应中原样返回；可用于在多并发场景下关联请求和结果
     * @param raw 包含一个 JSON 的原始字符串，前后允许存在任意非 JSON 字符
     * @return 包含 key 和格式化后 JSON 的文本块
     */
    @Tool(
            name = "format_json",
            description = "Extract the first valid JSON object or array from any text, format it "
                    + "with proper indentation, and return it wrapped with the caller-provided key. "
                    + "Returns: {\\\"key\\\": \\\"<your-key>\\\", \\\"json\\\": <formatted-json>}. "
                    + "Handles raw input with surrounding text (e.g. '\"Result: {\\\"key\\\": 123}\\n\"') "
                    + "by auto-locating and parsing only the first valid JSON. Works on code blocks, "
                    + "logs, API responses, or any mixed text. "
                    + "Call this when the user asks to format, beautify, or extract JSON.")
    public Mono<ToolResultBlock> formatJson(
            @ToolParam(name = "key", description = "A caller-provided identifier echoed back in "
                    + "the response. Use a unique value to correlate the response with your "
                    + "request when processing the tool call stream.")
            String key,
            @ToolParam(name = "raw", description = "The raw string that contains a JSON object "
                    + "or array. Extra text before/after the JSON will be ignored automatically. "
                    + "Valid JSON input is also accepted and will be re-formatted.")
            String raw) {
        return Mono.fromCallable(() -> {
            if (raw == null || raw.isBlank()) {
                String errorJson = JSON.toJSONString(
                        buildResult(key, "error", "raw 输入内容为空，无法解析"));
                return ToolResultBlock.text(errorJson);
            }

            // 1. 先尝试直接解析整个字符串（常见场景：传入的就是纯 JSON）
            String trimmed = raw.trim();
            Object directResult = tryParse(trimmed);
            if (directResult != null) {
                String resultJson = buildResultJson(key, directResult);
                return ToolResultBlock.text(resultJson);
            }

            // 2. 在字符串中查找第一个 JSON 起始位置
            int start = findJsonStart(trimmed);
            if (start < 0) {
                String errorJson = JSON.toJSONString(
                        buildResult(key, "error", "未找到 JSON 对象或数组（未找到 '{' 或 '['）"));
                return ToolResultBlock.text(errorJson);
            }

            // 3. 提取从 start 开始的完整 JSON 片段（处理嵌套括号）
            int end = findJsonEnd(trimmed, start);
            if (end < 0) {
                String errorJson = JSON.toJSONString(
                        buildResult(key, "error", "找到 JSON 起始位置但未找到匹配的结束括号，结构可能不完整"));
                return ToolResultBlock.text(errorJson);
            }

            String jsonFragment = trimmed.substring(start, end + 1);
            Object parsed = tryParse(jsonFragment);
            if (parsed == null) {
                String errorJson = JSON.toJSONString(
                        buildResult(key, "error", "提取的 JSON 片段语法有误，无法解析"));
                return ToolResultBlock.text(errorJson);
            }

            String resultJson = buildResultJson(key, parsed);
            return ToolResultBlock.text(resultJson);

        }).onErrorResume(e -> {
            log.warn("format_json 执行异常: {}", e.getMessage());
            return Mono.just(ToolResultBlock.error("JSON 格式化失败: " + e.getMessage()));
        });
    }

    /**
     * 尝试解析 JSON 字符串，返回 {@link JSONObject} 或 {@link JSONArray}。
     *
     * @param source 待解析的字符串
     * @return 解析成功返回 JSONObject 或 JSONArray；解析失败返回 null
     */
    private Object tryParse(String source) {
        try {
            JSONObject obj = JSON.parseObject(source);
            if (obj != null) {
                return obj;
            }
        } catch (Exception ignored) {
            // fall through
        }
        try {
            JSONArray arr = JSON.parseArray(source);
            if (arr != null) {
                return arr;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /**
     * 找到字符串中第一个 JSON 有效起始字符（'{' 或 '['）的位置。
     * <p>
     * 跳过字符串字面量内部的括号，防止匹配到引号内的 JSON 外观文本（例如 "他说{你好}"）。
     *
     * @param s 待搜索的字符串（已 trim）
     * @return 起始索引，未找到返回 -1
     */
    private int findJsonStart(String s) {
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !isEscaped(s, i)) {
                inString = !inString;
            } else if (!inString && (c == '{' || c == '[')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从给定的起始位置（'{' 或 '['）找到匹配的结束括号位置。
     * <p>
     * 使用栈做括号匹配，同时正确处理字符串字面量和转义字符。
     *
     * @param s     完整字符串
     * @param start 起始位置（必须是 '{' 或 '['）
     * @return 匹配的结束括号位置，未找到返回 -1
     */
    private int findJsonEnd(String s, int start) {
        char openBracket = s.charAt(start);
        char closeBracket = (openBracket == '{') ? '}' : ']';

        Deque<Character> stack = new ArrayDeque<>();
        stack.push(openBracket);
        boolean inString = false;

        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !isEscaped(s, i)) {
                inString = !inString;
            } else if (!inString) {
                if (c == openBracket) {
                    stack.push(c);
                } else if (c == closeBracket) {
                    stack.pop();
                    if (stack.isEmpty()) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 判断指定位置的字符是否被反斜杠转义。
     * <p>
     * 考虑连续反斜杠的情况：例如 {@code \\"} 中引号被转义，而 {@code \\\"} 中反斜杠被转义而引号未转义。
     *
     * @param s   完整字符串
     * @param pos 字符位置
     * @return true 如果该位置的字符被转义
     */
    private boolean isEscaped(String s, int pos) {
        int backslashCount = 0;
        for (int i = pos - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            backslashCount++;
        }
        return backslashCount % 2 == 1;
    }

    /**
     * 构建完整的响应 JSON：{@code {"key":"<key>","json":<formatted>}}。
     *
     * @param key    调用方传入的请求标识
     * @param parsed 已解析的 JSON 对象或数组
     * @return 包含 key 和格式化后 JSON 的字符串
     */
    private String buildResultJson(String key, Object parsed) {
        JSONObject result = new JSONObject();
        result.put("key", key != null ? key : "");
        result.put("json", parsed);
        return JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 构建异常响应 JSON：{@code {"key":"<key>","json":<error info>}}。
     *
     * @param key   调用方传入的请求标识
     * @param type  错误类型
     * @param error 错误描述
     * @return 错误结果 JSON 对象
     */
    private JSONObject buildResult(String key, String type, String error) {
        JSONObject result = new JSONObject();
        result.put("key", key != null ? key : "");
        result.put("error", type);
        result.put("message", error);
        return result;
    }
}