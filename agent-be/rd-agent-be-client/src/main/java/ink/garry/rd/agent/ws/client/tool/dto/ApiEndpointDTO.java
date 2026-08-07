package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API 端点 DTO（application 层边界；对应 domain ApiEndpoint 值对象，FC 手动录入单端点）。
 * <p>详见工具管理技术方案 §7.6。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiEndpointDTO {

    /** 请求方式：GET / POST / PUT / DELETE / PATCH。 */
    private String method;

    /** 端点路径（以 / 开头；可含 {paramName} 占位）。 */
    private String path;

    /** 端点用途描述（≤200 字符）。 */
    private String description;

    /** Query 参数列表（可选）。 */
    private List<ApiParamDTO> queryParams;

    /** Path 参数列表（可选）；须与 path 占位符一一对应。 */
    private List<ApiParamDTO> pathParams;

    /** 请求头列表（可选）。 */
    private List<ApiHeaderDTO> headers;
}
