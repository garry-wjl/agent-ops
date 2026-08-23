package ink.garry.rd.agent.ws.application.tool.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 JSON Schema（含 default / 嵌套 object/array/map）生成试连用默认 body。
 */
public final class JsonSchemaDefaultValueBuilder {

    private JsonSchemaDefaultValueBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static Object build(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        if (schema.containsKey("default")) {
            return schema.get("default");
        }
        String type = stringType(schema.get("type"));
        if ("object".equals(type) || schema.get("properties") != null
                || schema.get("additionalProperties") != null) {
            Object additional = schema.get("additionalProperties");
            Map<String, Object> props = asMap(schema.get("properties"));
            boolean mapLike = additional != null && (props == null || props.isEmpty()
                    || "map".equals(String.valueOf(schema.get("x-field-kind"))));
            if (mapLike) {
                Map<String, Object> sample = new LinkedHashMap<>();
                Object valueDefault = additional instanceof Map<?, ?> m
                        ? build((Map<String, Object>) m) : "value";
                if (valueDefault != null) {
                    sample.put("key", valueDefault);
                }
                return sample;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            if (props != null) {
                for (Map.Entry<String, Object> e : props.entrySet()) {
                    Object child = build(asMap(e.getValue()));
                    if (child != null) {
                        out.put(e.getKey(), child);
                    }
                }
            }
            return out;
        }
        if ("array".equals(type)) {
            Object items = build(asMap(schema.get("items")));
            List<Object> list = new ArrayList<>();
            if (items != null) {
                list.add(items);
            }
            return list;
        }
        return null;
    }

    private static String stringType(Object type) {
        if (type == null) {
            return null;
        }
        if (type instanceof List<?> list && CollUtil.isNotEmpty(list)) {
            return String.valueOf(list.get(0));
        }
        return String.valueOf(type);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    public static String preview(String body, int max) {
        if (StrUtil.isBlank(body)) {
            return "";
        }
        if (body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "...";
    }
}
