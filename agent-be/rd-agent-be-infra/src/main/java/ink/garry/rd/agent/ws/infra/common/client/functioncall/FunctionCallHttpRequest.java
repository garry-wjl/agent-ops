package ink.garry.rd.agent.ws.infra.common.client.functioncall;

import java.util.Map;

/**
 * FunctionCall 工具运行时 HTTP 请求(中立模型)。
 * <p>
 * 由 application 层 {@code FunctionCallTool} 装配实参后构造,交 {@link FunctionCallInvoker} 执行;
 * 刻意不引入 hutool / 具体 HTTP 库类型,保持 application 与底层 HTTP 实现解耦。
 *
 * @param method         HTTP 方法名(GET/POST/PUT/DELETE/PATCH,对应 {@code HttpMethod} 枚举名)
 * @param url            已完成 path 占位替换的目标 URL(baseUrl + path,不含 query 串)
 * @param queryParams    query 参数(已按默认值兜底;由执行方负责 URL 编码后拼到 query 串)
 * @param headers        端点配置的请求头(字面值,已按默认值兜底;优先级高于透传头)
 * @param inboundHeaders 入站请求透传头(在请求线程抓取后随工具链传入;执行方按黑名单过滤后注入,
 *                       端点头覆盖之);可空
 */
public record FunctionCallHttpRequest(
        String method,
        String url,
        Map<String, String> queryParams,
        Map<String, String> headers,
        Map<String, String> inboundHeaders) {
}
