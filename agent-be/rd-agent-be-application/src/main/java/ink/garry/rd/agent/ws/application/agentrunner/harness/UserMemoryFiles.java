package ink.garry.rd.agent.ws.application.agentrunner.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 长期记忆文件的相对路径与按日账本合并。路径相对工作空间根，不带会话前缀。
 */
public final class UserMemoryFiles {

    public static final String MEMORY_MD = "MEMORY.md";

    private UserMemoryFiles() {
    }

    /**
     * @param day 日期
     * @return {@code memory/yyyy-MM-dd.md}
     */
    public static String dailyRelative(LocalDate day) {
        return "memory/" + day + ".md";
    }

    /**
     * @param json    已有账本 JSON，可空
     * @param day     要写入的日期
     * @param content 当日正文；空白则不改该日
     * @return 合并后的 JSON
     */
    public static String mergeDaily(String json, LocalDate day, String content) {
        Map<String, String> map = parseDaily(json);
        if (content != null && !content.isBlank()) {
            map.put(day.toString(), content);
        }
        return JSON.toJSONString(map);
    }

    /**
     * @param json 账本 JSON
     * @return 日期到正文；解析失败返回空 map
     */
    public static Map<String, String> parseDaily(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = JSON.parseObject(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (RuntimeException ex) {
            return new LinkedHashMap<>();
        }
    }
}
