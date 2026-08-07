package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MCP 代理透传请求头值对象（贫血模型，详见 PRD §7.5）。
 * <p>
 * 仅在 {@code proxyEnabled=true} 时可填，单工具上限 20 条；header 名工具内不重复（大小写不敏感）。
 * {@link #value} 支持两种模式：① 直接字面值（如 {@code application/json}）；
 * ② 变量占位符 {@code {变量名}}（如 {@code {userToken}}，运行时由平台上下文注入）。
 * <p>
 * 变量占位符的运行时解析（BuiltinVariableRegistry 等）不在领域层，本值对象仅承载配置 + 格式校验依据。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProxyHeader {

    /** 请求头名（如 Authorization、X-Trace-Id）；工具内大小写不敏感唯一。 */
    private String name;

    /** 请求头值；字面值或变量占位符 {@code {字母数字下划线}}。 */
    private String value;

    /** 描述（可选）。 */
    private String description;
}
