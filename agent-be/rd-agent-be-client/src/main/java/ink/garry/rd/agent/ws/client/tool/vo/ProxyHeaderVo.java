package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * MCP 代理透传请求头入参 / 出参 Vo（adapter 层；对应 ProxyHeaderDTO）。
 * <p>详见工具管理技术方案 §7.5。value 支持字面值或变量占位符 {@code {变量名}}。
 */
@Data
public class ProxyHeaderVo {

    /** 请求头名（工具内大小写不敏感唯一）。 */
    private String name;

    /** 请求头值；字面值或变量占位符 {@code {字母数字下划线}}。 */
    private String value;

    /** 描述（可选）。 */
    private String description;
}
