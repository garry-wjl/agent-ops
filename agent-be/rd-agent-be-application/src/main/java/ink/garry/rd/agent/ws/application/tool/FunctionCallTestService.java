package ink.garry.rd.agent.ws.application.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.application.tool.factory.ToolRunnerFactory;
import ink.garry.rd.agent.ws.application.tool.support.JsonSchemaDefaultValueBuilder;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.FcTestConnectionParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.FcTestConnectionResultDTO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpRequest;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpResponse;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FunctionCall 一键试连：按录入/OpenAPI 端点 + 默认值发真实 HTTP，判断网络是否可达。
 */
@Slf4j
@Service
public class FunctionCallTestService {

    private static final int PREVIEW_MAX = 2000;
    private static final int SAMPLE_MAX = 50_000;

    @Resource
    private FunctionCallInvoker functionCallInvoker;
    @Resource
    private ToolRunnerFactory toolRunnerFactory;

    public FcTestConnectionResultDTO test(FcTestConnectionParamDTO param) {
        if (param == null) {
            throw new BusinessException(7005, "试连参数不能为空");
        }
        String baseUrl;
        ApiEndpointDTO endpoint;
        if (StrUtil.isNotBlank(param.getOpenApiSpec())) {
            try {
                JSONObject root = JSON.parseObject(param.getOpenApiSpec());
                baseUrl = toolRunnerFactory.resolveOpenApiBaseUrl(root, param.getBaseUrl());
                List<ApiEndpointDTO> endpoints = toolRunnerFactory.resolveOpenApiEndpoints(root);
                endpoint = pickEndpoint(endpoints, param.getEndpoint());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("OpenAPI 试连解析失败", e);
                throw new BusinessException(7004, "OpenAPI 文档解析失败: " + e.getMessage());
            }
        } else {
            baseUrl = param.getBaseUrl();
            endpoint = param.getEndpoint();
        }
        if (StrUtil.isBlank(baseUrl)) {
            throw new BusinessException(7005, "Base URL 不能为空");
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new BusinessException(7005, "Base URL 必须包含 http:// 或 https://");
        }
        if (endpoint == null || StrUtil.isBlank(endpoint.getMethod()) || StrUtil.isBlank(endpoint.getPath())) {
            throw new BusinessException(7005, "请指定要测试的端点（method + path）");
        }

        String path;
        try {
            path = substitutePath(endpoint.getPath(), endpoint.getPathParams());
        } catch (IllegalArgumentException e) {
            return FcTestConnectionResultDTO.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestMethod(endpoint.getMethod())
                    .requestUrl(StrUtil.removeSuffix(baseUrl, "/") + endpoint.getPath())
                    .build();
        }

        Map<String, String> query = buildQuery(endpoint.getQueryParams());
        Map<String, String> headers = buildHeaders(endpoint.getHeaders());
        String body = resolveBody(param.getBody(), endpoint.getRequestBodySchema());

        String url = StrUtil.removeSuffix(baseUrl, "/") + path;
        FunctionCallHttpRequest request = new FunctionCallHttpRequest(
                endpoint.getMethod().toUpperCase(),
                url,
                query,
                headers,
                null,
                body);

        long start = System.currentTimeMillis();
        try {
            FunctionCallHttpResponse response = functionCallInvoker.invoke(request);
            long latency = System.currentTimeMillis() - start;
            boolean ok = response.status() > 0;
            return FcTestConnectionResultDTO.builder()
                    .success(ok)
                    .message(ok
                            ? ("连通成功，HTTP " + response.status())
                            : "未拿到有效 HTTP 状态码")
                    .httpStatus(response.status())
                    .latencyMs(latency)
                    .requestMethod(request.method())
                    .requestUrl(url)
                    .responsePreview(JsonSchemaDefaultValueBuilder.preview(response.body(), PREVIEW_MAX))
                    .responseSample(sampleForFill(response.status(), response.body()))
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.info("FunctionCall 试连失败: {} {}", request.method(), url, e);
            return FcTestConnectionResultDTO.builder()
                    .success(false)
                    .message("连通失败: " + e.getMessage())
                    .latencyMs(latency)
                    .requestMethod(request.method())
                    .requestUrl(url)
                    .build();
        }
    }

    private static ApiEndpointDTO pickEndpoint(List<ApiEndpointDTO> endpoints, ApiEndpointDTO hint) {
        if (CollUtil.isEmpty(endpoints)) {
            throw new BusinessException(7004, "OpenAPI 文档未解析到任何可测端点");
        }
        if (hint != null && StrUtil.isNotBlank(hint.getMethod()) && StrUtil.isNotBlank(hint.getPath())) {
            String m = hint.getMethod().toUpperCase();
            String p = hint.getPath();
            for (ApiEndpointDTO ep : endpoints) {
                if (m.equalsIgnoreCase(ep.getMethod()) && p.equals(ep.getPath())) {
                    // 若 hint 带了覆盖 schema/body 元数据，优先用解析结果，仅匹配身份
                    return ep;
                }
            }
            throw new BusinessException(7005, "OpenAPI 中找不到端点 " + m + " " + p);
        }
        return endpoints.get(0);
    }

    private static String substitutePath(String path, List<ApiParamDTO> pathParams) {
        String result = path;
        if (CollUtil.isNotEmpty(pathParams)) {
            for (ApiParamDTO param : pathParams) {
                String value = StrUtil.blankToDefault(param.getDefaultValue(), null);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Path 参数缺少默认值，无法试连: " + param.getName());
                }
                result = result.replace("{" + param.getName() + "}", value);
            }
        }
        return result;
    }

    private static Map<String, String> buildQuery(List<ApiParamDTO> queryParams) {
        Map<String, String> query = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(queryParams)) {
            for (ApiParamDTO param : queryParams) {
                if (StrUtil.isNotBlank(param.getDefaultValue())) {
                    query.put(param.getName(), param.getDefaultValue());
                }
            }
        }
        return query;
    }

    private static Map<String, String> buildHeaders(List<ApiHeaderDTO> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(headers)) {
            for (ApiHeaderDTO header : headers) {
                if (StrUtil.isNotBlank(header.getDefaultValue())) {
                    out.put(header.getName(), header.getDefaultValue());
                }
            }
        }
        return out;
    }

    private static String resolveBody(Map<String, Object> explicit, Map<String, Object> schema) {
        if (explicit != null && !explicit.isEmpty()) {
            return JSON.toJSONString(explicit);
        }
        if (CollUtil.isEmpty(schema)) {
            return null;
        }
        Object built = JsonSchemaDefaultValueBuilder.build(schema);
        return built == null ? null : JSON.toJSONString(built);
    }

    /**
     * HTTP 200 且 body 为合法 JSON、长度未超限时返回原样，供前端推断返回参数结构。
     */
    private static String sampleForFill(int status, String body) {
        if (status != 200 || StrUtil.isBlank(body) || body.length() > SAMPLE_MAX) {
            return null;
        }
        try {
            JSON.parse(body);
            return body;
        } catch (Exception e) {
            return null;
        }
    }
}
