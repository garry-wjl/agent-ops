package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * API 请求头值对象（贫血模型，FunctionCall 手动录入端点的 headers，详见 PRD §7.6）。
 * <p>
 * 与 {@link ProxyHeader} 区别：此处<b>不支持</b>变量占位符（仅字面值），变量占位仅 MCP 代理透传场景，
 * 避免心智混乱。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiHeader {

    /** 请求头名（合法 Header 名）。 */
    private String name;

    /** 默认值（可选）；字面值，不支持变量占位。 */
    private String defaultValue;

    /** 描述（必填，≤200 字符）。 */
    private String description;
}
