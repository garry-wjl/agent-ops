package ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P0 内置评估器编码白名单。
 */
public enum BuiltinGraderCode {
    /** 输出非空 */
    NON_EMPTY,
    /** 与 reference 精确匹配 */
    EXACT_MATCH,
    /** 包含关键词 */
    CONTAINS,
    /** 合法 JSON */
    JSON_VALID,
    /** 轨迹中存在工具调用 */
    TOOL_CALLED,
    /** 轨迹/toolNames 含指定关键词 */
    TOOL_NAME_CONTAINS;

    private static final Set<String> CODES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    /**
     * 是否为合法内置编码。
     *
     * @param code 编码
     * @return true=白名单内
     */
    public static boolean isValid(String code) {
        return code != null && CODES.contains(code);
    }
}
