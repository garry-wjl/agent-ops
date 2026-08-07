package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

import java.util.List;

/**
 * API 端点入参 / 出参 Vo（adapter 层；对应 ApiEndpointDTO，FC 手动录入单端点）。
 */
@Data
public class ApiEndpointVo {

    /** 请求方式：GET / POST / PUT / DELETE / PATCH。 */
    private String method;

    /** 端点路径（以 / 开头；可含 {paramName} 占位）。 */
    private String path;

    /** 端点用途描述（≤200 字符）。 */
    private String description;

    /** Query 参数列表（可选）。 */
    private List<ApiParamVo> queryParams;

    /** Path 参数列表（可选）；须与 path 占位符一一对应。 */
    private List<ApiParamVo> pathParams;

    /** 请求头列表（可选）。 */
    private List<ApiHeaderVo> headers;
}
