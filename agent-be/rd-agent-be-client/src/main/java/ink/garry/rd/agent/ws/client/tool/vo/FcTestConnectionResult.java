package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * FunctionCall 一键试连结果。
 */
@Data
public class FcTestConnectionResult {

    /** 是否判定为连通（HTTP 有响应即 true；网络/超时为 false）。 */
    private boolean success;

    /** 人类可读摘要。 */
    private String message;

    /** HTTP 状态码（网络失败时为 null）。 */
    private Integer httpStatus;

    /** 耗时毫秒。 */
    private Long latencyMs;

    /** 实际请求 URL（含 query）。 */
    private String requestUrl;

    /** 实际 HTTP 方法。 */
    private String requestMethod;

    /** 响应体截断预览。 */
    private String responsePreview;

    /** HTTP 200 且可解析为 JSON 时的完整样例（供一键填充返回参数）。 */
    private String responseSample;
}
