package ink.garry.rd.agent.ws.application.tool.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI requestBody / schema 解析：抽取 {@code application/json} schema，并递归展开本地 {@code $ref}
 *（{@code #/components/schemas/...}），供 FunctionCall 端点持久化为完整 JSON Schema。
 */
public final class OpenApiSchemaResolver {

    /** 防环 / 防爆炸：嵌套展开深度上限。 */
    private static final int MAX_DEPTH = 32;

    private OpenApiSchemaResolver() {
    }

    /**
     * 从 operation 抽取 requestBody schema；优先 {@code application/json}，否则首个含 json 的 media type，
     * 再否则取 content 首项。无 requestBody / schema 时返回 null。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractRequestBodySchema(JSONObject operation, JSONObject root) {
        if (operation == null) {
            return null;
        }
        JSONObject requestBody = operation.getJSONObject("requestBody");
        if (requestBody == null) {
            return null;
        }
        JSONObject content = requestBody.getJSONObject("content");
        if (content == null || content.isEmpty()) {
            return null;
        }
        JSONObject media = content.getJSONObject("application/json");
        if (media == null) {
            for (String key : content.keySet()) {
                String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
                if (lower.contains("json")) {
                    media = content.getJSONObject(key);
                    break;
                }
            }
        }
        if (media == null) {
            String first = content.keySet().iterator().next();
            media = content.getJSONObject(first);
        }
        if (media == null) {
            return null;
        }
        Object schema = media.get("schema");
        Object resolved = resolve(schema, root, new HashSet<>(), 0);
        if (resolved instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /** OpenAPI {@code requestBody.required}；无 requestBody 时返回 null。 */
    public static Boolean extractRequestBodyRequired(JSONObject operation) {
        if (operation == null) {
            return null;
        }
        JSONObject requestBody = operation.getJSONObject("requestBody");
        if (requestBody == null || !requestBody.containsKey("required")) {
            return null;
        }
        return requestBody.getBoolean("required");
    }

    /**
     * 递归展开 schema 节点中的本地 {@code $ref}；环引用保留原始 {@code $ref} 节点避免死循环。
     */
    @SuppressWarnings("unchecked")
    static Object resolve(Object node, JSONObject root, Set<String> visiting, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return node;
        }
        if (node instanceof JSONObject obj) {
            String ref = obj.getString("$ref");
            if (StrUtil.isNotBlank(ref)) {
                if (!visiting.add(ref)) {
                    Map<String, Object> cycle = new LinkedHashMap<>();
                    cycle.put("$ref", ref);
                    return cycle;
                }
                Object target = resolveRef(ref, root);
                Object resolved = resolve(target, root, visiting, depth + 1);
                visiting.remove(ref);
                if (obj.size() > 1 && resolved instanceof Map<?, ?> base) {
                    Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) base);
                    for (String key : obj.keySet()) {
                        if ("$ref".equals(key)) {
                            continue;
                        }
                        merged.put(key, resolve(obj.get(key), root, visiting, depth + 1));
                    }
                    return merged;
                }
                return resolved;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : obj.keySet()) {
                out.put(key, resolve(obj.get(key), root, visiting, depth + 1));
            }
            return out;
        }
        if (node instanceof JSONArray arr) {
            List<Object> list = new ArrayList<>(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                list.add(resolve(arr.get(i), root, visiting, depth + 1));
            }
            return list;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), resolve(e.getValue(), root, visiting, depth + 1));
            }
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolve(item, root, visiting, depth + 1));
            }
            return out;
        }
        return node;
    }

    /** 仅支持文档内指针 {@code #/a/b/c}。 */
    static Object resolveRef(String ref, JSONObject root) {
        if (root == null || StrUtil.isBlank(ref) || !ref.startsWith("#/")) {
            return null;
        }
        String[] parts = ref.substring(2).split("/");
        Object cur = root;
        for (String part : parts) {
            if (!(cur instanceof JSONObject jo)) {
                return null;
            }
            cur = jo.get(part);
        }
        return cur;
    }
}
