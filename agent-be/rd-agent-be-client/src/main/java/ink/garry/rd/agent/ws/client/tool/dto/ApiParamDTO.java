package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 参数 DTO（application 层边界；对应 domain ApiParam 值对象，用于 query / path 参数）。
 * <p>详见工具管理技术方案 §7.6。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiParamDTO {

    /** 参数名（合法变量名）。 */
    private String name;

    /** 参数数据类型：STRING / NUMBER / BOOLEAN / INTEGER。 */
    private String type;

    /** 默认值（可选，字符串形式）。 */
    private String defaultValue;

    /**
     * 是否必填（写入 Agent Tool Schema 的 required）；path 参数恒为 true。
     * 可空：兼容旧数据时由运行时按「无默认值则必填」兜底。
     */
    private Boolean required;

    /** 描述（≤200 字符）。 */
    private String description;
}
