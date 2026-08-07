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

    /** 描述（≤200 字符）。 */
    private String description;
}
