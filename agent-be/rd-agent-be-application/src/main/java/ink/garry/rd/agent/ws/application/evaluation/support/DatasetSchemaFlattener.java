package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评测集 Schema 层级展开：将嵌套 object/array 铺成点路径列，供表单与 xlsx 导入导出。
 * <p>
 * 路径约定：{@code context.orderId}、{@code tags[0]}、{@code items[0].sku}。
 * 数组在模板中按固定槽位展开（默认 3 个下标）。
 */
public final class DatasetSchemaFlattener {

    /** 默认数组槽位数（模板/导入表头） */
    public static final int DEFAULT_ARRAY_SLOTS = 3;

    /** 最大嵌套深度（根字段为第 1 层） */
    public static final int MAX_DEPTH = 3;

    private static final Pattern SEGMENT =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\[(\\d+)])?$");

    private DatasetSchemaFlattener() {
    }

    /**
     * 根据 schemaJson（字段数组）生成扁平列名。
     *
     * @param schemaJson schema JSON
     * @param arraySlots 数组展开槽位数
     * @return 列名列表
     */
    public static List<String> columnHeaders(String schemaJson, int arraySlots) {
        int slots = Math.max(1, arraySlots);
        List<JSONObject> fields = parseFields(schemaJson);
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        if (fields.isEmpty()) {
            cols.add("input");
            cols.add("reference");
            cols.add("context");
            return new ArrayList<>(cols);
        }
        for (JSONObject field : fields) {
            String name = field.getString("name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            expandField(cols, name, field, 1, slots);
        }
        return new ArrayList<>(cols);
    }

    /**
     * 将扁平行（点路径列）还原为嵌套 Map，供写入 dataJson。
     *
     * @param flat 扁平键值（表头→单元格文本）
     * @return 嵌套结构
     */
    public static Map<String, Object> unflatten(Map<String, String> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (flat == null || flat.isEmpty()) {
            return root;
        }
        for (Map.Entry<String, String> e : flat.entrySet()) {
            String path = e.getKey();
            String raw = e.getValue();
            if (StrUtil.isBlank(path) || StrUtil.isBlank(raw)) {
                continue;
            }
            Object value = coerceCell(raw.trim());
            putPath(root, path.trim(), value);
        }
        return pruneEmpty(root);
    }

    /**
     * 将 dataJson 嵌套对象按列名展平为单元格字符串。
     *
     * @param dataJson 行 JSON
     * @param columns  目标列（通常来自 schema）
     * @return 列→值
     */
    public static Map<String, String> flattenToColumns(String dataJson, List<String> columns) {
        Map<String, String> out = new LinkedHashMap<>();
        Object root = StrUtil.isBlank(dataJson) ? new JSONObject() : JSON.parse(dataJson);
        if (!(root instanceof Map<?, ?> map)) {
            return out;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) map;
        List<String> cols = columns;
        if (cols == null || cols.isEmpty()) {
            cols = new ArrayList<>(dynamicFlattenKeys(nested, "", 1));
        }
        for (String col : cols) {
            Object v = getPath(nested, col);
            out.put(col, valueToCell(v));
        }
        return out;
    }

    /**
     * 合并 schema 列与数据中实际出现的扁平键（导出时补齐）。
     */
    public static List<String> mergeColumns(List<String> schemaCols, List<Map<String, Object>> nestedRows) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (schemaCols != null) {
            set.addAll(schemaCols);
        }
        if (nestedRows != null) {
            for (Map<String, Object> row : nestedRows) {
                set.addAll(dynamicFlattenKeys(row, "", 1));
            }
        }
        if (set.isEmpty()) {
            set.add("input");
            set.add("reference");
            set.add("context");
        }
        return new ArrayList<>(set);
    }

    private static void expandField(Set<String> cols, String path, JSONObject field,
                                    int depth, int arraySlots) {
        String type = normalizeType(field.getString("type"));
        if ("object".equals(type)) {
            JSONObject props = field.getJSONObject("properties");
            if (props != null && !props.isEmpty() && depth < MAX_DEPTH) {
                for (String key : props.keySet()) {
                    Object child = props.get(key);
                    JSONObject childObj = asObject(child);
                    expandField(cols, path + "." + key, childObj, depth + 1, arraySlots);
                }
                return;
            }
            cols.add(path);
            return;
        }
        if ("array".equals(type)) {
            if (depth >= MAX_DEPTH) {
                cols.add(path);
                return;
            }
            JSONObject items = field.getJSONObject("items");
            if (items == null) {
                for (int i = 0; i < arraySlots; i++) {
                    cols.add(path + "[" + i + "]");
                }
                return;
            }
            String itemType = normalizeType(items.getString("type"));
            if ("object".equals(itemType)) {
                JSONObject props = items.getJSONObject("properties");
                if (props != null && !props.isEmpty() && depth + 1 <= MAX_DEPTH) {
                    for (int i = 0; i < arraySlots; i++) {
                        String base = path + "[" + i + "]";
                        for (String key : props.keySet()) {
                            JSONObject childObj = asObject(props.get(key));
                            expandField(cols, base + "." + key, childObj, depth + 1, arraySlots);
                        }
                    }
                    return;
                }
            }
            for (int i = 0; i < arraySlots; i++) {
                cols.add(path + "[" + i + "]");
            }
            return;
        }
        cols.add(path);
    }

    private static List<JSONObject> parseFields(String schemaJson) {
        List<JSONObject> list = new ArrayList<>();
        if (StrUtil.isBlank(schemaJson)) {
            return list;
        }
        try {
            Object parsed = JSON.parse(schemaJson.trim());
            if (parsed instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject o = asObject(arr.get(i));
                    if (o != null && StrUtil.isNotBlank(o.getString("name"))) {
                        list.add(o);
                    }
                }
            }
        } catch (Exception ignored) {
            // 非法 schema 时返回空，由调用方回退默认列
        }
        return list;
    }

    private static JSONObject asObject(Object o) {
        if (o instanceof JSONObject jo) {
            return jo;
        }
        if (o instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        return new JSONObject();
    }

    private static String normalizeType(String type) {
        if (StrUtil.isBlank(type)) {
            return "string";
        }
        return type.trim().toLowerCase();
    }

    private static Object coerceCell(String raw) {
        if ((raw.startsWith("{") && raw.endsWith("}"))
                || (raw.startsWith("[") && raw.endsWith("]"))) {
            try {
                return JSON.parse(raw);
            } catch (Exception ignored) {
                return raw;
            }
        }
        return raw;
    }

    private static void putPath(Map<String, Object> root, String path, Object value) {
        List<PathToken> tokens = tokenize(path);
        if (tokens.isEmpty()) {
            return;
        }
        Object cursor = root;
        for (int i = 0; i < tokens.size(); i++) {
            PathToken t = tokens.get(i);
            boolean last = i == tokens.size() - 1;
            if (t.index == null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) cursor;
                if (last) {
                    map.put(t.name, value);
                    return;
                }
                Object next = map.get(t.name);
                PathToken nextTok = tokens.get(i + 1);
                if (nextTok.index != null) {
                    if (!(next instanceof List)) {
                        next = new ArrayList<>();
                        map.put(t.name, next);
                    }
                } else {
                    if (!(next instanceof Map)) {
                        next = new LinkedHashMap<String, Object>();
                        map.put(t.name, next);
                    }
                }
                cursor = next;
            } else {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) cursor;
                ensureSize(list, t.index + 1);
                if (last) {
                    list.set(t.index, value);
                    return;
                }
                Object next = list.get(t.index);
                PathToken nextTok = tokens.get(i + 1);
                if (nextTok.index != null) {
                    if (!(next instanceof List)) {
                        next = new ArrayList<>();
                        list.set(t.index, next);
                    }
                } else {
                    if (!(next instanceof Map)) {
                        next = new LinkedHashMap<String, Object>();
                        list.set(t.index, next);
                    }
                }
                cursor = next;
            }
        }
    }

    private static Object getPath(Map<String, Object> root, String path) {
        List<PathToken> tokens = tokenize(path);
        Object cursor = root;
        for (PathToken t : tokens) {
            if (cursor == null) {
                return null;
            }
            if (t.index == null) {
                if (!(cursor instanceof Map<?, ?> map)) {
                    return null;
                }
                cursor = map.get(t.name);
            } else {
                // token like name[i] where list is under name — handled as name then index
                // Our tokenizer splits tags[0] into name=tags,index=0 when on map; when cursor is list, name may be null
                if (t.name != null) {
                    if (!(cursor instanceof Map<?, ?> map)) {
                        return null;
                    }
                    cursor = map.get(t.name);
                }
                if (!(cursor instanceof List<?> list)) {
                    return null;
                }
                if (t.index < 0 || t.index >= list.size()) {
                    return null;
                }
                cursor = list.get(t.index);
            }
        }
        return cursor;
    }

    private static List<PathToken> tokenize(String path) {
        List<PathToken> tokens = new ArrayList<>();
        if (StrUtil.isBlank(path)) {
            return tokens;
        }
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (StrUtil.isBlank(part)) {
                continue;
            }
            Matcher m = SEGMENT.matcher(part);
            if (!m.matches()) {
                tokens.add(new PathToken(part, null));
                continue;
            }
            String name = m.group(1);
            Integer idx = m.group(2) == null ? null : Integer.parseInt(m.group(2));
            if (idx == null) {
                tokens.add(new PathToken(name, null));
            } else {
                // tags[0] → map key tags, then list index 0
                tokens.add(new PathToken(name, null));
                tokens.add(new PathToken(null, idx));
            }
        }
        return tokens;
    }

    private static void ensureSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add(null);
        }
    }

    private static Set<String> dynamicFlattenKeys(Map<String, Object> map, String prefix, int depth) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (map == null || depth > MAX_DEPTH) {
            if (StrUtil.isNotBlank(prefix)) {
                keys.add(prefix);
            }
            return keys;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            if (StrUtil.isBlank(key)) {
                continue;
            }
            String path = StrUtil.isBlank(prefix) ? key : prefix + "." + key;
            Object val = e.getValue();
            if (val instanceof Map<?, ?> child && depth < MAX_DEPTH) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) child;
                if (childMap.isEmpty()) {
                    keys.add(path);
                } else {
                    keys.addAll(dynamicFlattenKeys(childMap, path, depth + 1));
                }
            } else if (val instanceof List<?> list && depth < MAX_DEPTH) {
                if (list.isEmpty()) {
                    keys.add(path + "[0]");
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        String base = path + "[" + i + "]";
                        if (item instanceof Map<?, ?> im && depth + 1 <= MAX_DEPTH) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) im;
                            keys.addAll(dynamicFlattenKeys(itemMap, base, depth + 1));
                        } else {
                            keys.add(base);
                        }
                    }
                }
            } else {
                keys.add(path);
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pruneEmpty(Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof String s && StrUtil.isBlank(s)) {
                continue;
            }
            if (v instanceof Map<?, ?> m) {
                Map<String, Object> pruned = pruneEmpty((Map<String, Object>) m);
                if (!pruned.isEmpty()) {
                    out.put(e.getKey(), pruned);
                }
                continue;
            }
            if (v instanceof List<?> list) {
                List<Object> cleaned = new ArrayList<>();
                for (Object item : list) {
                    if (item == null) {
                        continue;
                    }
                    if (item instanceof String s && StrUtil.isBlank(s)) {
                        continue;
                    }
                    if (item instanceof Map<?, ?> im) {
                        Map<String, Object> pruned = pruneEmpty((Map<String, Object>) im);
                        if (!pruned.isEmpty()) {
                            cleaned.add(pruned);
                        }
                    } else {
                        cleaned.add(item);
                    }
                }
                if (!cleaned.isEmpty()) {
                    out.put(e.getKey(), cleaned);
                }
                continue;
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    private static String valueToCell(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        return JSON.toJSONString(v);
    }

    private record PathToken(String name, Integer index) {
    }
}
