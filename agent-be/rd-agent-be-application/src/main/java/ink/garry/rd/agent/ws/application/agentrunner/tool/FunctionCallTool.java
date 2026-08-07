package ink.garry.rd.agent.ws.application.agentrunner.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpRequest;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpResponse;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FunctionCall 工具运行时实例:把一个手动录入端点(baseUrl + {@link ApiEndpointDTO})暴露成 Agent 可调用、
 * 可执行的工具。
 * <p>
 * 与 {@link SandboxTool} 同构:工具自身只做「实参装配 + 结果渲染」,真正的 HTTP 调用委托给 infra
 * 的 {@link FunctionCallInvoker};阻塞调用统一 {@code subscribeOn(boundedElastic)},不压 reactor
 * 事件循环线程。一个工具实例绑定单个端点,故每次 Agent 运行 new 一个,不做无状态单例 Bean。
 * <p>
 * 直接实现 {@link AgentTool}(而非 {@code @Tool} 注解式):FunctionCall 的参数 schema 是运行时才从
 * 工具元数据得知的动态结构,无法用编译期固定方法签名表达,故把 schema 作为 {@link #getParameters()} 暴露。
 */
@Slf4j
public class FunctionCallTool implements AgentTool {

    /** 函数名(LLM 兼容、工具内唯一)。 */
    private final String name;

    /** 函数描述(给 LLM 看)。 */
    private final String description;

    /** JSON Schema 参数对象({@code {type:object, properties, required}})。 */
    private final Map<String, Object> parameters;

    /** API Base URL(已去尾部斜杠)。 */
    private final String baseUrl;

    /** 绑定的端点(method / path / query / path / header 元数据)。 */
    private final ApiEndpointDTO endpoint;

    /** infra HTTP 执行器。 */
    private final FunctionCallInvoker invoker;

    /** 入站请求透传头(在请求线程抓取后传入,随工具执行注入出站调用);可空。 */
    private final Map<String, String> inboundHeaders;

    /**
     * @param name           函数名(由 {@code ToolRunnerFactory} 规整,保证 LLM 兼容且工具内唯一)
     * @param description    函数描述(通常取端点描述)
     * @param parameters     JSON Schema 参数对象
     * @param baseUrl        API Base URL
     * @param endpoint       绑定端点
     * @param invoker        infra HTTP 执行器
     * @param inboundHeaders 入站请求透传头(在请求线程抓取;可空)
     */
    public FunctionCallTool(String name,
                            String description,
                            Map<String, Object> parameters,
                            String baseUrl,
                            ApiEndpointDTO endpoint,
                            FunctionCallInvoker invoker,
                            Map<String, String> inboundHeaders) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.baseUrl = StrUtil.removeSuffix(StrUtil.nullToEmpty(baseUrl), "/");
        this.endpoint = endpoint;
        this.invoker = invoker;
        this.inboundHeaders = inboundHeaders;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * 执行端点调用:读取 LLM 实参 → 替换 path 占位 → 拼装 query / headers → 委托 invoker 发起 HTTP →
     * 渲染为 Agent 可读文本块。失败统一兜底为 error 块,不让异常击穿 reactor 流。
     */
    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
                    Map<String, Object> input = param.getInput();
                    String url = baseUrl + substitutePath(endpoint.getPath(), input);
                    FunctionCallHttpRequest request = new FunctionCallHttpRequest(
                            StrUtil.blankToDefault(endpoint.getMethod(), "GET"),
                            url,
                            buildQuery(input),
                            buildHeaders(),
                            inboundHeaders);
                    FunctionCallHttpResponse response = invoker.invoke(request);
                    return render(response);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("FunctionCall 工具调用失败, tool={}, path={}",
                            name, endpoint.getPath(), e);
                    return Mono.just(ToolResultBlock.error("工具调用失败: " + e.getMessage()));
                });
    }

    /** 用 path 参数实参(缺失则取默认值)替换 path 模板中的 {@code {name}} 占位。 */
    private String substitutePath(String path, Map<String, Object> input) {
        String result = path;
        if (CollUtil.isNotEmpty(endpoint.getPathParams())) {
            for (ApiParamDTO param : endpoint.getPathParams()) {
                String value = resolve(param, input);
                if (value == null) {
                    throw new IllegalArgumentException("缺少 path 参数: " + param.getName());
                }
                result = result.replace("{" + param.getName() + "}", value);
            }
        }
        return result;
    }

    /** 拼装 query 参数:LLM 实参优先,缺失取默认值,仍无则省略。 */
    private Map<String, String> buildQuery(Map<String, Object> input) {
        Map<String, String> query = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(endpoint.getQueryParams())) {
            for (ApiParamDTO param : endpoint.getQueryParams()) {
                String value = resolve(param, input);
                if (value != null) {
                    query.put(param.getName(), value);
                }
            }
        }
        return query;
    }

    /** 拼装请求头:取端点 header 字面默认值,空值省略。 */
    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(endpoint.getHeaders())) {
            for (ApiHeaderDTO header : endpoint.getHeaders()) {
                if (StrUtil.isNotBlank(header.getDefaultValue())) {
                    headers.put(header.getName(), header.getDefaultValue());
                }
            }
        }
        return headers;
    }

    /** 解析单个参数取值:LLM 实参优先,缺失回退默认值,均无返回 null。 */
    private String resolve(ApiParamDTO param, Map<String, Object> input) {
        Object raw = input == null ? null : input.get(param.getName());
        if (raw != null && StrUtil.isNotBlank(raw.toString())) {
            return raw.toString();
        }
        return StrUtil.isNotBlank(param.getDefaultValue()) ? param.getDefaultValue() : null;
    }

    /** 渲染响应:2xx 返回响应体文本;非 2xx 返回 error 块(带状态码 + 体)。 */
    private ToolResultBlock render(FunctionCallHttpResponse response) {
        if (response.isSuccess()) {
            return ToolResultBlock.text(StrUtil.isBlank(response.body())
                    ? "[HTTP " + response.status() + "] (empty body)"
                    : response.body());
        }
        return ToolResultBlock.error("HTTP " + response.status() + ": " + StrUtil.nullToEmpty(response.body()));
    }
}
