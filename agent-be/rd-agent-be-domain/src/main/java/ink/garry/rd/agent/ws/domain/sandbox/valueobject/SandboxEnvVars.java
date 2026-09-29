package ink.garry.rd.agent.ws.domain.sandbox.valueobject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 沙箱容器环境变量约束与归一化。
 * <p>
 * 存于沙箱资产，创建 OpenSandbox / Docker 容器时注入；非法 key / 超限直接拒绝。
 */
public final class SandboxEnvVars {

    /** 环境变量条数上限。 */
    public static final int MAX_ENTRIES = 50;

    /** 单 value 字符上限。 */
    public static final int MAX_VALUE_LENGTH = 4096;

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private SandboxEnvVars() {
    }

    /**
     * 归一化并校验环境变量表。
     * <ul>
     *   <li>null / 空 → 空 Map；</li>
     *   <li>key trim 后须匹配 shell 风格变量名；</li>
     *   <li>空 key 跳过；value 为 null 视为空串；</li>
     *   <li>条数 / value 长度超限抛 {@link IllegalArgumentException}。</li>
     * </ul>
     *
     * @param raw 原始 map（可空）
     * @return 不可变归一化结果（可能为空 Map）
     */
    public static Map<String, String> normalize(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = e.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "环境变量名非法：" + key + "（须匹配 [A-Za-z_][A-Za-z0-9_]*）");
            }
            String value = e.getValue() == null ? "" : e.getValue();
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "环境变量 " + key + " 的值超过 " + MAX_VALUE_LENGTH + " 字符");
            }
            if (out.containsKey(key)) {
                throw new IllegalArgumentException("环境变量名重复：" + key);
            }
            out.put(key, value);
        }
        if (out.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("环境变量最多 " + MAX_ENTRIES + " 条");
        }
        return Collections.unmodifiableMap(out);
    }
}
