package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * API 端点值对象（贫血模型，FunctionCall 手动录入的单个端点，详见 PRD §7.6）。
 * <p>
 * 单工具最多 50 个端点；{@link #path} 中的 {@code {paramName}} 占位须与 {@link #pathParams} 一一对应。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiEndpoint {

    /** 请求方式。 */
    private HttpMethod method;

    /** 端点路径（以 {@code /} 开头；可含 {@code {paramName}} 占位）。 */
    private String path;

    /** 端点用途描述（必填，≤200 字符；给 LLM 看）。 */
    private String description;

    /** Query 参数列表（可选）。 */
    private List<ApiParam> queryParams;

    /** Path 参数列表（可选）；须与 {@link #path} 中占位符一一对应。 */
    private List<ApiParam> pathParams;

    /** 请求头列表（可选）。 */
    private List<ApiHeader> headers;
}
