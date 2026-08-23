package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

import java.util.Map;

/**
 * FunctionCall 一键试连入参（不落库）：按 baseUrl + 端点（或 OpenAPI 原文 + 指定 method/path）发真实 HTTP。
 */
@Data
public class FcTestConnectionParam {

    /** API Base URL（MANUAL 必填；OpenAPI 可空，取 servers[0].url）。 */
    private String baseUrl;

    /** 待测端点（MANUAL 必填；OpenAPI 可仅填 method/path，schema 由后端从规范解析）。 */
    private ApiEndpointVo endpoint;

    /** OpenAPI JSON 原文（OPENAPI_SPEC 形态）。 */
    private String openApiSpec;

    /**
     * 可选：显式请求体（已按默认值展开）。为空时后端按 requestBodySchema / OpenAPI schema 生成默认 body。
     */
    private Map<String, Object> body;
}
