package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * API 请求头入参 / 出参 Vo（adapter 层；对应 ApiHeaderDTO，用于 FC 手动录入端点）。
 * <p>不支持变量占位（仅字面值）。
 */
@Data
public class ApiHeaderVo {

    /** 请求头名。 */
    private String name;

    /** 默认值（可选，字面值）。 */
    private String defaultValue;

    /** 描述（≤200 字符）。 */
    private String description;
}
