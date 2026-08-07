package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 请求头 DTO（application 层边界；对应 domain ApiHeader 值对象，用于 FC 手动录入端点）。
 * <p>详见工具管理技术方案 §7.6。不支持变量占位（仅字面值）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiHeaderDTO {

    /** 请求头名。 */
    private String name;

    /** 默认值（可选，字面值）。 */
    private String defaultValue;

    /** 描述（≤200 字符）。 */
    private String description;
}
