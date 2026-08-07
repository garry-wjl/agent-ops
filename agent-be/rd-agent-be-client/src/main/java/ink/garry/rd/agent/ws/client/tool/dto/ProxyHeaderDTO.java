package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 代理透传请求头 DTO（application 层边界；对应 domain ProxyHeader 值对象）。
 * <p>详见工具管理技术方案 §7.5。value 支持字面值或变量占位符 {@code {变量名}}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProxyHeaderDTO {

    /** 请求头名（工具内大小写不敏感唯一）。 */
    private String name;

    /** 请求头值；字面值或变量占位符 {@code {字母数字下划线}}。 */
    private String value;

    /** 描述（可选）。 */
    private String description;
}
