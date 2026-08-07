package ink.garry.rd.agent.ws.infra.common.client.functioncall;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * FunctionCall 工具运行时 HTTP 执行器(infra 端口)。
 * <p>
 * 使用 Hutool {@link HttpRequest} 实现:只负责按中立请求模型
 * {@link FunctionCallHttpRequest} 发起一次 HTTP 调用并回收 {@link FunctionCallHttpResponse},
 * <b>不</b>承担实参装配、占位替换、默认值兜底(那些在 application 层 {@code FunctionCallTool} 完成)。
 * <p>
 * 当前 FunctionCall 手动录入端点仅有 query / path / header 参数、无请求体,故 query 参数对所有
 * 方法统一拼到 query 串、不发送 body。
 */
@Slf4j
@Component
public class FunctionCallInvoker {

    /** 连接超时(毫秒)。 */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** 读取超时(毫秒)。 */
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * 不可透传的请求头(小写比较):这些是传输/实体层语义,随入站请求透传到出站会破坏目标调用
     * (如 Host 指向错误主机、Content-Length 与出站体不符、压缩/连接控制头错配)。
     */
    private static final Set<String> NON_FORWARDABLE_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "accept-encoding");

    /**
     * 执行一次 FunctionCall 工具 HTTP 调用。
     *
     * @param request 中立请求模型(方法 / URL / query / headers 均已装配完毕)
     * @return HTTP 响应(状态码 + 响应体);网络/协议异常由 Hutool 抛出,交调用方兜底
     */
    public FunctionCallHttpResponse invoke(FunctionCallHttpRequest request) {
        String url = request.url();
        if (CollUtil.isNotEmpty(request.queryParams())) {
            Map<String, Object> form = new LinkedHashMap<>(request.queryParams());
            url = HttpUtil.urlWithForm(url, form, StandardCharsets.UTF_8, false);
        }
        HttpRequest httpRequest = HttpUtil.createRequest(Method.valueOf(request.method()), url)
                .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS);
        // 1. 透传入站请求头(在请求线程抓取后随请求传入,取不到则空);过滤会破坏出站调用的传输/实体层头
        if (CollUtil.isNotEmpty(request.inboundHeaders())) {
            request.inboundHeaders().forEach((name, value) -> {
                if (name != null && !NON_FORWARDABLE_HEADERS.contains(name.toLowerCase())) {
                    httpRequest.header(name, value);
                }
            });
        }
        // 2. 端点配置的请求头覆盖透传头(显式配置优先)
        if (CollUtil.isNotEmpty(request.headers())) {
            request.headers().forEach(httpRequest::header);
        }
        log.debug("FunctionCall HTTP {} {}", request.method(), url);
        try (HttpResponse response = httpRequest.execute()) {
            return new FunctionCallHttpResponse(response.getStatus(), response.body());
        }
    }
}
