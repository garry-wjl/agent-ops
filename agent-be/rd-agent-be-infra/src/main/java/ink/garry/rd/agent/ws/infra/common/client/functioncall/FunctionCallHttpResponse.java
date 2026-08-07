package ink.garry.rd.agent.ws.infra.common.client.functioncall;

/**
 * FunctionCall 工具运行时 HTTP 响应(中立模型)。
 *
 * @param status HTTP 状态码
 * @param body   响应体文本(可能为空串)
 */
public record FunctionCallHttpResponse(int status, String body) {

    /** 是否 2xx 成功。 */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
