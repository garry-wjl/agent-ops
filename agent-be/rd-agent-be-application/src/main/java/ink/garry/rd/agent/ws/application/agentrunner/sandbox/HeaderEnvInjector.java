package ink.garry.rd.agent.ws.application.agentrunner.sandbox;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 将 Invoke 入站请求头按 Agent 配置映射为沙箱环境变量。
 * <p>
 * 仅当 {@code headerEnvInjectEnabled=true} 且映射表非空时产出条目；
 * 请求头按 HTTP 语义大小写不敏感匹配；非法 env 名跳过。
 */
public final class HeaderEnvInjector {

    /** POSIX 风格环境变量名。 */
    private static final Pattern ENV_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private HeaderEnvInjector() {
    }

    /**
     * 按快照开关与映射表，从入站头解析沙箱 env。
     *
     * @param snapshot Agent 配置快照（可空）
     * @param headers  入站请求头（可空；key 保留原始大小写）
     * @return 有序 env Map（可能为空，不为 null）
     */
    public static Map<String, String> resolve(
            AgentDTO.ConfigSnapshot snapshot, Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (snapshot == null || !Boolean.TRUE.equals(snapshot.getHeaderEnvInjectEnabled())) {
            return out;
        }
        List<AgentDTO.ConfigSnapshot.HeaderEnvMapping> mappings = snapshot.getHeaderEnvMappings();
        if (mappings == null || mappings.isEmpty() || headers == null || headers.isEmpty()) {
            return out;
        }
        Map<String, String> lowerIndex = indexByLowerName(headers);
        for (AgentDTO.ConfigSnapshot.HeaderEnvMapping mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String headerName = StrUtil.trim(mapping.getHeaderName());
            String envName = StrUtil.trim(mapping.getEnvName());
            if (StrUtil.isBlank(headerName) || StrUtil.isBlank(envName)) {
                continue;
            }
            if (!ENV_NAME.matcher(envName).matches()) {
                continue;
            }
            String value = lowerIndex.get(headerName.toLowerCase(Locale.ROOT));
            if (value == null) {
                continue;
            }
            out.put(envName, value);
        }
        return out;
    }

    /**
     * 将非空 env 以 {@code export KEY='val'; ...; } 前缀拼到 shell 命令前。
     * <p>OpenSandbox {@code runInSession} 无 per-command env，靠此前缀注入；
     * Docker mock 路径已用 {@code docker exec -e}，调用方勿对 Docker 重复包装。
     *
     * @param env     环境变量（可空）
     * @param command 原始命令（可空则原样返回）
     * @return 包装后的命令
     */
    public static String prefixExports(Map<String, String> env, String command) {
        if (command == null) {
            return null;
        }
        if (env == null || env.isEmpty()) {
            return command;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            String key = e.getKey();
            if (StrUtil.isBlank(key) || !ENV_NAME.matcher(key).matches()) {
                continue;
            }
            sb.append("export ")
                    .append(key)
                    .append('=')
                    .append(shellSingleQuote(e.getValue()))
                    .append("; ");
        }
        if (sb.isEmpty()) {
            return command;
        }
        return sb.append(command).toString();
    }

    private static Map<String, String> indexByLowerName(Map<String, String> headers) {
        Map<String, String> lower = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            lower.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
        }
        return lower;
    }

    /** 单引号包裹，内部单引号按 shell 惯例转义。 */
    static String shellSingleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
