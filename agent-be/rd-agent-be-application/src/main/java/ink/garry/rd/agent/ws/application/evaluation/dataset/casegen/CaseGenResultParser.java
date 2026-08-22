package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 生成器 Agent 输出解析：JSON 数组 / JSONL / markdown fence；按 Schema 顶层字段名校验，不合格跳过。
 */
public final class CaseGenResultParser {

    private CaseGenResultParser() {
    }

    /** 解析结果。 */
    public record ParseOutcome(List<String> validDataJsonList, int parsedCount, int skippedCount) {
    }

    /**
     * @param rawOutput  Agent 文本输出
     * @param schemaJson 评测集 schema
     * @param maxWrite   最多写入条数（硬上限）
     */
    public static ParseOutcome parse(String rawOutput, String schemaJson, int maxWrite) {
        List<JSONObject> candidates = extractObjects(rawOutput);
        Set<String> topFields = topLevelFieldNames(schemaJson);
        List<String> valid = new ArrayList<>();
        int skipped = 0;
        int limit = Math.max(0, maxWrite);
        for (JSONObject obj : candidates) {
            if (obj == null || obj.isEmpty()) {
                skipped++;
                continue;
            }
            if (!matchesSchema(obj, topFields)) {
                skipped++;
                continue;
            }
            if (valid.size() >= limit) {
                skipped++;
                continue;
            }
            valid.add(JSON.toJSONString(obj));
        }
        return new ParseOutcome(valid, candidates.size(), skipped);
    }

    static List<JSONObject> extractObjects(String raw) {
        List<JSONObject> out = new ArrayList<>();
        if (StrUtil.isBlank(raw)) {
            return out;
        }
        String text = stripCodeFence(raw.trim());
        // 1) JSON 数组
        try {
            Object parsed = JSON.parse(text);
            if (parsed instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    Object el = arr.get(i);
                    if (el instanceof JSONObject jo) {
                        out.add(jo);
                    } else if (el instanceof String s && StrUtil.isNotBlank(s)) {
                        try {
                            Object inner = JSON.parse(s.trim());
                            if (inner instanceof JSONObject jo) {
                                out.add(jo);
                            }
                        } catch (Exception ignored) {
                            // skip
                        }
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
            if (parsed instanceof JSONObject jo) {
                out.add(jo);
                return out;
            }
        } catch (Exception ignored) {
            // fall through to JSONL
        }
        // 2) JSONL
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (StrUtil.isBlank(t) || !t.startsWith("{")) {
                continue;
            }
            try {
                Object el = JSON.parse(t);
                if (el instanceof JSONObject jo) {
                    out.add(jo);
                }
            } catch (Exception ignored) {
                // skip line
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        // 3) 从全文抠第一个 [...] 或 {...}
        int arrStart = text.indexOf('[');
        int arrEnd = text.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            try {
                Object parsed = JSON.parse(text.substring(arrStart, arrEnd + 1));
                if (parsed instanceof JSONArray arr) {
                    for (int i = 0; i < arr.size(); i++) {
                        Object el = arr.get(i);
                        if (el instanceof JSONObject jo) {
                            out.add(jo);
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return out;
    }

    static String stripCodeFence(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            return t;
        }
        int lastFence = t.lastIndexOf("```");
        if (lastFence <= firstNl) {
            return t;
        }
        return t.substring(firstNl + 1, lastFence).trim();
    }

    static Set<String> topLevelFieldNames(String schemaJson) {
        Set<String> names = new LinkedHashSet<>();
        if (StrUtil.isBlank(schemaJson)) {
            return names;
        }
        try {
            Object parsed = JSON.parse(schemaJson.trim());
            if (parsed instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    Object el = arr.get(i);
                    if (el instanceof JSONObject jo) {
                        String name = jo.getString("name");
                        if (StrUtil.isNotBlank(name)) {
                            names.add(name);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // empty
        }
        return names;
    }

    /**
     * Schema 有字段时：至少一个顶层字段命中；无 schema 字段时：只要非空对象。
     */
    static boolean matchesSchema(JSONObject obj, Set<String> topFields) {
        if (topFields == null || topFields.isEmpty()) {
            return true;
        }
        for (String f : topFields) {
            if (obj.containsKey(f)) {
                return true;
            }
        }
        return false;
    }
}
