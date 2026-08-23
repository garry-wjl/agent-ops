package ink.garry.rd.agent.ws.infra.common.client.functioncall;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
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
 * query 参数对所有方法统一拼到 query 串;若 {@link FunctionCallHttpRequest#body()} 非空则发送
 * JSON 请求体(默认 {@code Content-Type: application/json},端点显式头可覆盖)。
 */
@Slf4j
@Component
public class FunctionCallInvoker {

    /** 连接超时(毫秒)。 */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** 读取超时(毫秒)。 */
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * 不可透传的请求头(小写比较)。
     * <p>
     * 传输 / 实体层头随入站透传到出站会破坏目标调用；内容协商头同理——
     * 调试台 SSE 入站常带 {@code Accept: text/event-stream}，透传到业务 API 会触发 HTTP 406
     * （与 MCP 入站黑名单对齐，见 {@code ToolRunnerFactory.DEFAULT_NON_FORWARDABLE_HEADERS}）。
     */
    static final Set<String> NON_FORWARDABLE_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "accept-encoding",
            "accept", "content-type");

    /**
     * 执行一次 FunctionCall 工具 HTTP 调用。
     *
     * @param request 中立请求模型(方法 / URL / query / headers / body 均已装配完毕)
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
        // 1. 透传入站请求头(过滤传输层 / 内容协商头)；2. 端点配置头覆盖透传头
        mergeHeaders(request.inboundHeaders(), request.headers())
                .forEach(httpRequest::header);
        // 3. JSON body:端点未显式 Content-Type 时补 application/json
        //    （不再看入站 Content-Type——入站头已不透传 content-type）
        if (StrUtil.isNotBlank(request.body())) {
            if (!hasHeaderIgnoreCase(request.headers(), "content-type")) {
                httpRequest.header("Content-Type", "application/json");
            }
            httpRequest.body(request.body());
        }
        log.debug("FunctionCall HTTP {} {}", request.method(), url);
        try (HttpResponse response = httpRequest.execute()) {
            return new FunctionCallHttpResponse(response.getStatus(), response.body());
        }
    }

    /**
     * 合并出站头：入站透传（黑名单过滤）→ 端点配置覆盖。
     * package-visible 供单测断言，不发起真实 HTTP。
     */
    static Map<String, String> mergeHeaders(
            Map<String, String> inboundHeaders,
            Map<String, String> endpointHeaders) {
        Map<String, String> out = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(inboundHeaders)) {
            inboundHeaders.forEach((name, value) -> {
                if (isForwardableInboundHeader(name)) {
                    out.put(name, value);
                }
            });
        }
        if (CollUtil.isNotEmpty(endpointHeaders)) {
            endpointHeaders.forEach(out::put);
        }
        return out;
    }

    static boolean isForwardableInboundHeader(String name) {
        return name != null && !NON_FORWARDABLE_HEADERS.contains(name.toLowerCase());
    }

    private static boolean hasHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (CollUtil.isEmpty(headers) || name == null) {
            return false;
        }
        for (String key : headers.keySet()) {
            if (key != null && name.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }
}
