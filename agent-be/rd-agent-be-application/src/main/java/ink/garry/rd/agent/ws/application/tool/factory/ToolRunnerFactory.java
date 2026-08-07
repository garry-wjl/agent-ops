package ink.garry.rd.agent.ws.application.tool.factory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.application.agentrunner.tool.FunctionCallTool;
import ink.garry.rd.agent.ws.application.tool.ToolQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionResultDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ApiParamType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.McpConfigType;
import ink.garry.rd.agent.ws.domain.tool.valueobject.PackageMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolRunner 工厂：把工具管理沉淀的 FunctionCall 工具元数据翻译成 agentscope 运行时可消费的对象，供
 * {@code AgentRunnerFactory} 在拼装 {@code Toolkit} 时按 {@code ConfigSnapshot.toolNums} 逐个注册。
 * <p>
 * 两种产物：
 * <ul>
 *   <li>{@link #buildTools(ToolDTO, Map)} → 可执行 {@link AgentTool}（{@link FunctionCallTool}），让 Agent
 *       <b>自主执行</b> HTTP 调用；覆盖 FunctionCall（MANUAL / OPENAPI_SPEC）与 MCP API 打包-EXISTING_API，
 *       运行时聚合用，<b>容错跳过</b>暂不支持的工具形态，不抛异常以免拖垮整个 Agent 装配。</li>
 *   <li>{@link #buildSchemas(ToolDTO)} → 纯 {@link ToolSchema}（schema-only），用于外部执行
 *       （{@code Toolkit.registerSchema} → 挂起回传）场景；严格校验，形态不符即抛业务异常。</li>
 *   <li>{@link #buildMcpClient(ToolDTO, Map)} → {@link McpClientWrapper}，按 {@code mcpConfigType}
 *       选 stdio / sse / streamable-http 传输；REMOTE 形态注入用户配置透传头 + 入站请求头。</li>
 * </ul>
 * <p>
 * <b>职责边界</b>：工具元数据由调用层经 {@link ToolQueryService#findByNum(String)} 加载后以 {@link ToolDTO}
 * 传入（本工厂不再直连查询，仅 EXISTING_API 委托时按来源 num 二次解析）；真正的 HTTP 调用下沉 infra
 * {@link FunctionCallInvoker}。
 * <p>
 * <b>形态适配</b>：一个 {@link ToolType#FUNCTION_CALL} 工具可声明多个端点（{@link CreationMode#MANUAL}
 * 下 1~50 个），每个端点都是 LLM 可独立调用的一个函数，故两个 build 方法均返回 List（一端点一产物）。
 */
@Slf4j
@Component
public class ToolRunnerFactory {

    /** OpenAI function name 合法字符（其余字符统一替换为下划线）。 */
    private static final java.util.regex.Pattern ILLEGAL_NAME_CHARS =
            java.util.regex.Pattern.compile("[^A-Za-z0-9_-]+");
    /** function name 长度上限（OpenAI / 多数厂商约束 64）。 */
    private static final int FUNCTION_NAME_MAX_LENGTH = 64;

    /** MCP 远程连接请求 / 初始化超时（秒）。 */
    private static final int MCP_TIMEOUT_SECONDS = 30;

    /**
     * 不可透传的请求头（小写比较）：传输 / 实体层语义，随入站请求透传到出站会破坏目标调用
     * （Host 指向错误主机、Content-Length 与出站体不符、压缩 / 连接控制头错配）。
     */
    private static final Set<String> NON_FORWARDABLE_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "accept-encoding");

    /** OpenAPI path-item 中视为「可执行端点」的 HTTP 方法（与 {@code HttpMethod} 枚举对齐，其余如 head/options 跳过）。 */
    private static final Set<String> OPENAPI_METHODS = Set.of("get", "post", "put", "delete", "patch");

    /**
     * OpenAPI 规范保留的 header 参数名（小写比较）：{@code in=header} 且名为这三者时，参数定义
     * <b>必须被忽略</b>（由内容协商 / 安全方案处理，详见 OpenAPI 3.x Parameter Object）。
     */
    private static final Set<String> OPENAPI_RESERVED_HEADERS = Set.of("accept", "content-type", "authorization");

    @Resource
    private ToolQueryService toolQueryService;

    @Resource
    private FunctionCallInvoker functionCallInvoker;

    /**
     * 按工具编码构建一组可执行工具（一端点一 {@link FunctionCallTool}），供 Agent 自主执行。
     * <p>
     * 覆盖以下「可暴露为可调用函数」的工具：
     * <ul>
     *   <li>{@link ToolType#FUNCTION_CALL} + {@link CreationMode#MANUAL}：按本工具端点（{@code endpoints}）直接构建；</li>
     *   <li>{@link ToolType#FUNCTION_CALL} + {@link CreationMode#OPENAPI_SPEC}：解析 {@code openApiSpec} 原文
     *       （baseUrl 取 {@code servers[0].url}，每个 path×method 一端点，query / path 参数转 JSON Schema）后构建；</li>
     *   <li>{@link ToolType#MCP} + API 打包 / {@link PackageMode#EXISTING_API}：引用一个已发布 FC 工具
     *       （{@code sourceFcToolNum}）、动态跟随其端点，故<b>委托</b>按来源 FC 工具构建（语义等价，
     *       无需真起 MCP server）。</li>
     * </ul>
     * 其余形态（MCP 远程连接 / API 打包-OPENAPI_PASTE 等）当前运行时未支持，<b>记日志并返回空列表</b>
     * （容错跳过），避免单个工具拖垮整个 Agent 的工具装配。
     *
     * @param tool           工具 DTO（由调用层经 {@code ToolQueryService.findByNum} 加载后传入）
     * @param inboundHeaders 入站请求透传头（须在请求线程抓取后传入，因工具实际在异步线程执行、
     *                       取不到当前请求上下文；可空）
     * @return 该工具各端点对应的可执行工具列表；不支持的形态返回空列表
     * @throws BusinessException EXISTING_API 形态的来源 FC 工具不存在时
     */
    public List<AgentTool> buildTools(ToolDTO tool, Map<String, String> inboundHeaders) {
        // MCP API 打包-已有 API：引用一个 FC 工具、动态跟随其端点 → 委托按来源 FC 工具构建
        if (ToolType.MCP.name().equals(tool.getType())
                && PackageMode.EXISTING_API.name().equals(tool.getPackageMode())) {
            if (StrUtil.isBlank(tool.getSourceFcToolNum())) {
                log.warn("MCP API 打包工具 {} 缺少来源 FC 工具编号(sourceFcToolNum)，已跳过", tool.getNum());
                return Collections.emptyList();
            }
            return buildTools(toolQueryService.findByNum(tool.getSourceFcToolNum()), inboundHeaders);
        }

        if (!ToolType.FUNCTION_CALL.name().equals(tool.getType())) {
            log.debug("工具 {} 类型为 {}，buildTools 跳过（非 FunctionCall / 非 API打包-已有API）",
                    tool.getNum(), tool.getType());
            return Collections.emptyList();
        }

        // 按创建方式取 baseUrl + 端点：MANUAL 直接用录入端点；OPENAPI_SPEC 解析 spec 原文
        String baseUrl;
        List<ApiEndpointDTO> endpoints;
        if (CreationMode.MANUAL.name().equals(tool.getCreationMode())) {
            baseUrl = tool.getBaseUrl();
            endpoints = tool.getEndpoints();
        } else if (CreationMode.OPENAPI_SPEC.name().equals(tool.getCreationMode())) {
            if (StrUtil.isBlank(tool.getOpenApiSpec())) {
                log.warn("工具 {} 为 OPENAPI_SPEC 形态但 openApiSpec 为空，已跳过", tool.getNum());
                return Collections.emptyList();
            }
            try {
                JSONObject root = JSON.parseObject(tool.getOpenApiSpec());
                baseUrl = openApiBaseUrl(root, tool.getBaseUrl());
                endpoints = parseOpenApiEndpoints(root);
            } catch (Exception e) {
                log.warn("工具 {} 的 openApiSpec 解析失败，已跳过", tool.getNum(), e);
                return Collections.emptyList();
            }
        } else {
            log.warn("工具 {} 创建方式为 {}，运行时暂仅支持 MANUAL / OPENAPI_SPEC，已跳过",
                    tool.getNum(), tool.getCreationMode());
            return Collections.emptyList();
        }

        if (CollUtil.isEmpty(endpoints)) {
            log.warn("工具 {} 未解析到任何 API 端点，已跳过", tool.getNum());
            return Collections.emptyList();
        }

        List<AgentTool> tools = new ArrayList<>(endpoints.size());
        for (ApiEndpointDTO endpoint : endpoints) {
            tools.add(new FunctionCallTool(
                    functionName(tool, endpoint),
                    description(tool, endpoint),
                    buildParameters(endpoint),
                    baseUrl,
                    endpoint,
                    functionCallInvoker,
                    inboundHeaders));
        }
        return tools;
    }

    /**
     * 按工具编码加载 FunctionCall 工具定义元数据，构建一组 {@link ToolSchema}（schema-only）。
     * <p>
     * 仅支持 {@link ToolType#FUNCTION_CALL} + {@link CreationMode#MANUAL}。每个端点映射为一个独立
     * 的 {@link ToolSchema}：{@code name} 为规整后的合法函数名，{@code description} 取端点描述，
     * {@code parameters} 为按端点 query / path 参数装配的 JSON Schema。
     *
     * @param tool 工具 DTO（由调用层经 {@code ToolQueryService.findByNum} 加载后传入）
     * @return 该工具各端点对应的 ToolSchema 列表（非空）
     * @throws BusinessException 非 FunctionCall 类型，或创建方式暂不支持时
     */
    public List<ToolSchema> buildSchemas(ToolDTO tool) {
        if (!ToolType.FUNCTION_CALL.name().equals(tool.getType())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "工具 " + tool.getNum() + " 类型为 " + tool.getType() + "，buildSchemas 仅支持 FUNCTION_CALL 工具");
        }
        if (!CreationMode.MANUAL.name().equals(tool.getCreationMode())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "工具 " + tool.getNum() + " 创建方式为 " + tool.getCreationMode()
                            + "，当前仅支持手动录入（MANUAL）FunctionCall 工具的 schema 构建");
        }
        if (CollUtil.isEmpty(tool.getEndpoints())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "工具 " + tool.getNum() + " 未配置任何 API 端点，无法构建 ToolSchema");
        }

        List<ToolSchema> schemas = new ArrayList<>(tool.getEndpoints().size());
        for (ApiEndpointDTO endpoint : tool.getEndpoints()) {
            schemas.add(ToolSchema.builder()
                    .name(functionName(tool, endpoint))
                    .description(description(tool, endpoint))
                    .parameters(buildParameters(endpoint))
                    .build());
        }
        return schemas;
    }

    /**
     * 按工具编码构建 MCP 客户端连接（{@link McpClientWrapper}），供 {@code AgentRunnerFactory} 经
     * {@code toolkit.registerMcpClient(wrapper).block()} 注册。
     * <p>
     * 仅处理 {@link ToolType#MCP} + {@link CreationMode#REMOTE}（MCP 远程连接形态）；按 {@code mcpConfigType}
     * 选传输方式：
     * <ul>
     *   <li>{@link McpConfigType#LOCAL} → stdio 传输（mcpConfig 取 {@code command/args/env}），无 HTTP 头注入；</li>
     *   <li>{@link McpConfigType#REMOTE} → 按 {@code transport} 选 sse / streamable-http 传输（mcpConfig 取
     *       {@code url/transport/headers}），并按优先级注入请求头：入站透传头（黑名单过滤，最低）→
     *       mcpConfig.headers → 用户配置透传头 proxyHeaders（最高）。</li>
     * </ul>
     * <p>
     * <b>容错</b>：非 MCP 类型返回 {@code null}（供运行时与 {@link #buildTools(ToolDTO, Map)} 同列表分流，
     * 不抛异常）；MCP 但形态/配置非法时抛业务异常，由调用方决定是否跳过。
     *
     * @param tool           工具 DTO（由调用层经 {@code ToolQueryService.findByNum} 加载后传入）
     * @param inboundHeaders 入站请求透传头（请求线程抓取后传入；仅 REMOTE 生效；可空）
     * @return MCP 客户端（未初始化，由 registerMcpClient 时初始化）；非 MCP 工具返回 {@code null}
     * @throws BusinessException MCP 形态暂不支持，或 mcpConfig 配置非法时
     */
    public McpClientWrapper buildMcpClient(ToolDTO tool, Map<String, String> inboundHeaders) {
        if (!ToolType.MCP.name().equals(tool.getType())) {
            log.debug("工具 {} 类型为 {}，非 MCP，buildMcpClient 跳过", tool.getNum(), tool.getType());
            return null;
        }
        if (!CreationMode.REMOTE.name().equals(tool.getCreationMode())) {
            // API 打包形态：EXISTING_API 由 buildTools 按来源 FC 工具构建；OPENAPI_PASTE 暂未支持（需 OpenAPI 解析）
            log.debug("工具 {} 创建方式为 {}，buildMcpClient 跳过（API 打包形态由 buildTools 分流或暂未支持）",
                    tool.getNum(), tool.getCreationMode());
            return null;
        }
        if (StrUtil.isBlank(tool.getMcpConfig())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "工具 " + tool.getNum() + " 未配置 mcpConfig");
        }

        JSONObject config = parseServerConfig(tool.getMcpConfig());
        McpClientBuilder builder = McpClientBuilder.create(mcpClientName(tool));

        if (McpConfigType.LOCAL.name().equals(tool.getMcpConfigType())) {
            String command = config.getString("command");
            if (StrUtil.isBlank(command)) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "工具 " + tool.getNum() + " 的 MCP LOCAL 配置缺少 command");
            }
            List<String> args = config.containsKey("args")
                    ? config.getJSONArray("args").toJavaList(String.class) : Collections.emptyList();
            Map<String, String> env = toStringMap(config.getJSONObject("env"));
            builder.stdioTransport(command, args, env);
        } else {
            String url = config.getString("url");
            if (StrUtil.isBlank(url)) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "工具 " + tool.getNum() + " 的 MCP REMOTE 配置缺少 url");
            }
            String transport = StrUtil.blankToDefault(config.getString("transport"), config.getString("type"));
            if (transport != null && transport.toLowerCase().contains("sse")) {
                builder.sseTransport(url);
            } else {
                builder.streamableHttpTransport(url);
            }
            builder.headers(buildMcpHeaders(config, tool, inboundHeaders))
                    .timeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS))
                    .initializationTimeout(Duration.ofSeconds(MCP_TIMEOUT_SECONDS));
        }
        return builder.buildSync();
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 端点描述兜底工具描述。 */
    private String description(ToolDTO tool, ApiEndpointDTO endpoint) {
        return StrUtil.isNotBlank(endpoint.getDescription()) ? endpoint.getDescription() : tool.getDescription();
    }

    /** OpenAPI baseUrl：取 {@code servers[0].url}；缺失回退工具 baseUrl 字段，仍无则空串。 */
    private String openApiBaseUrl(JSONObject root, String fallback) {
        JSONArray servers = root == null ? null : root.getJSONArray("servers");
        if (servers != null && !servers.isEmpty()) {
            JSONObject first = servers.getJSONObject(0);
            if (first != null && StrUtil.isNotBlank(first.getString("url"))) {
                return first.getString("url");
            }
        }
        return StrUtil.nullToEmpty(fallback);
    }

    /**
     * 解析 OpenAPI {@code paths} 为端点列表（每个 path×method 一端点）。
     * <p>合并 path-item 级与 operation 级 {@code parameters}；取 {@code in=query/path} 作为 LLM 入参、
     * {@code in=header} 作为端点请求头（取 {@code schema.default} 为字面值，与 MANUAL 头一致由
     * {@code FunctionCallTool} 注入；按 OpenAPI 规范忽略 Accept/Content-Type/Authorization）；
     * 另解析 {@code apiKey + in=header} 的 {@code securityScheme}（按 operation / 根 {@code security} 生效）
     * 为认证请求头；{@code cookie} 与 {@code $ref} 引用参数跳过；方法限 {@link #OPENAPI_METHODS}。
     */
    private List<ApiEndpointDTO> parseOpenApiEndpoints(JSONObject root) {
        List<ApiEndpointDTO> endpoints = new ArrayList<>();
        JSONObject paths = root == null ? null : root.getJSONObject("paths");
        if (paths == null) {
            return endpoints;
        }
        // apiKey-in-header 安全方案（schemeName → headerName）+ 根级 security 需求
        Map<String, String> apiKeyHeaders = apiKeyHeaderSchemes(root);
        JSONArray rootSecurity = root.getJSONArray("security");

        for (String path : paths.keySet()) {
            JSONObject pathItem = paths.getJSONObject(path);
            if (pathItem == null) {
                continue;
            }
            // path-item 级共享参数
            List<ApiParamDTO> sharedQuery = new ArrayList<>();
            List<ApiParamDTO> sharedPath = new ArrayList<>();
            List<ApiHeaderDTO> sharedHeader = new ArrayList<>();
            collectParams(pathItem.getJSONArray("parameters"), sharedQuery, sharedPath, sharedHeader);

            for (String key : pathItem.keySet()) {
                String method = key.toLowerCase();
                if (!OPENAPI_METHODS.contains(method)) {
                    continue;
                }
                JSONObject operation = pathItem.getJSONObject(key);
                if (operation == null) {
                    continue;
                }
                List<ApiParamDTO> query = new ArrayList<>(sharedQuery);
                List<ApiParamDTO> pathParams = new ArrayList<>(sharedPath);
                List<ApiHeaderDTO> headers = new ArrayList<>(sharedHeader);
                collectParams(operation.getJSONArray("parameters"), query, pathParams, headers);
                // operation 级 security 覆盖根级（含显式空数组表示禁用）
                JSONArray security = operation.containsKey("security")
                        ? operation.getJSONArray("security") : rootSecurity;
                headers.addAll(resolveSecurityHeaders(security, apiKeyHeaders));

                endpoints.add(ApiEndpointDTO.builder()
                        .method(method.toUpperCase())
                        .path(path)
                        .description(StrUtil.blankToDefault(operation.getString("summary"),
                                StrUtil.blankToDefault(operation.getString("description"), path)))
                        .queryParams(query.isEmpty() ? null : query)
                        .pathParams(pathParams.isEmpty() ? null : pathParams)
                        .headers(headers.isEmpty() ? null : headers)
                        .build());
            }
        }
        return endpoints;
    }

    /** 收集 {@code components.securitySchemes} 中 {@code type=apiKey} 且 {@code in=header} 的方案：schemeName → headerName。 */
    private Map<String, String> apiKeyHeaderSchemes(JSONObject root) {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject components = root.getJSONObject("components");
        JSONObject schemes = components == null ? null : components.getJSONObject("securitySchemes");
        if (schemes == null) {
            return result;
        }
        for (String schemeName : schemes.keySet()) {
            JSONObject scheme = schemes.getJSONObject(schemeName);
            if (scheme != null
                    && "apiKey".equalsIgnoreCase(scheme.getString("type"))
                    && "header".equalsIgnoreCase(scheme.getString("in"))
                    && StrUtil.isNotBlank(scheme.getString("name"))) {
                result.put(schemeName, scheme.getString("name"));
            }
        }
        return result;
    }

    /**
     * 把 {@code security} 需求解析为认证请求头（仅 apiKey-in-header 方案）。
     * <p>OpenAPI 标准里 apiKey 需求的值数组应为空（scope 仅 oauth2 用）；云效等厂商把 token 直接放进该
     * 数组首元素，这里按此约定取 token 作为 header 字面值（无值则只读到 header 名、运行时不注入）。
     */
    private List<ApiHeaderDTO> resolveSecurityHeaders(JSONArray security, Map<String, String> apiKeyHeaders) {
        List<ApiHeaderDTO> headers = new ArrayList<>();
        if (security == null || apiKeyHeaders.isEmpty()) {
            return headers;
        }
        for (int i = 0; i < security.size(); i++) {
            JSONObject requirement = security.getJSONObject(i);
            if (requirement == null) {
                continue;
            }
            for (String schemeName : requirement.keySet()) {
                String headerName = apiKeyHeaders.get(schemeName);
                if (headerName == null) {
                    continue;
                }
                JSONArray values = requirement.getJSONArray(schemeName);
                String token = (values != null && !values.isEmpty()) ? values.getString(0) : null;
                headers.add(ApiHeaderDTO.builder()
                        .name(headerName)
                        .defaultValue(token)
                        .description("API Key（OpenAPI securityScheme: " + schemeName + "）")
                        .build());
            }
        }
        return headers;
    }

    /** 从 OpenAPI {@code parameters} 数组按 {@code in} 分拣到 query / path / header（跳过 $ref、cookie 及无名参数）。 */
    private void collectParams(JSONArray params, List<ApiParamDTO> query,
                               List<ApiParamDTO> pathParams, List<ApiHeaderDTO> headers) {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            JSONObject param = params.getJSONObject(i);
            if (param == null || param.containsKey("$ref")) {
                continue;
            }
            String name = param.getString("name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            JSONObject schema = param.getJSONObject("schema");
            Object defaultValue = schema == null ? null : schema.get("default");
            String defaultStr = defaultValue == null ? null : String.valueOf(defaultValue);
            String description = param.getString("description");
            String in = param.getString("in");
            if ("query".equalsIgnoreCase(in)) {
                query.add(ApiParamDTO.builder()
                        .name(name)
                        .type(openApiTypeToParamType(schema == null ? null : schema.getString("type")))
                        .defaultValue(defaultStr)
                        .description(description)
                        .build());
            } else if ("path".equalsIgnoreCase(in)) {
                pathParams.add(ApiParamDTO.builder()
                        .name(name)
                        .type(openApiTypeToParamType(schema == null ? null : schema.getString("type")))
                        .defaultValue(defaultStr)
                        .description(description)
                        .build());
            } else if ("header".equalsIgnoreCase(in)) {
                // OpenAPI 规范：header 参数名为 Accept / Content-Type / Authorization 时必须忽略
                if (OPENAPI_RESERVED_HEADERS.contains(name.toLowerCase())) {
                    continue;
                }
                headers.add(ApiHeaderDTO.builder()
                        .name(name)
                        .defaultValue(defaultStr)
                        .description(description)
                        .build());
            }
        }
    }

    /** OpenAPI schema type → {@link ApiParamType} 枚举名；未知兜底 STRING。 */
    private String openApiTypeToParamType(String type) {
        if (StrUtil.isBlank(type)) {
            return ApiParamType.STRING.name();
        }
        return switch (type.toLowerCase()) {
            case "integer" -> ApiParamType.INTEGER.name();
            case "number" -> ApiParamType.NUMBER.name();
            case "boolean" -> ApiParamType.BOOLEAN.name();
            default -> ApiParamType.STRING.name();
        };
    }

    /** 解析 mcpConfig JSON 为单个 server 配置对象；兼容 {@code {mcpServers:{<name>:{...}}}} 包裹形式（取首个）。 */
    private JSONObject parseServerConfig(String mcpConfig) {
        JSONObject root = JSON.parseObject(mcpConfig);
        if (root != null && root.containsKey("mcpServers")) {
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers != null && !servers.isEmpty()) {
                return servers.getJSONObject(servers.keySet().iterator().next());
            }
        }
        return root == null ? new JSONObject() : root;
    }

    /**
     * 装配 MCP 远程连接请求头，优先级由低到高（后者覆盖前者）：
     * 入站透传头（黑名单过滤）→ mcpConfig.headers → 用户配置透传头 proxyHeaders（proxyEnabled 时）。
     */
    private Map<String, String> buildMcpHeaders(JSONObject config, ToolDTO tool, Map<String, String> inboundHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(inboundHeaders)) {
            inboundHeaders.forEach((name, value) -> {
                if (name != null && !NON_FORWARDABLE_HEADERS.contains(name.toLowerCase())) {
                    headers.put(name, value);
                }
            });
        }
        headers.putAll(toStringMap(config.getJSONObject("headers")));
        if (Boolean.TRUE.equals(tool.getProxyEnabled()) && CollUtil.isNotEmpty(tool.getProxyHeaders())) {
            for (ProxyHeaderDTO ph : tool.getProxyHeaders()) {
                if (StrUtil.isNotBlank(ph.getName())) {
                    headers.put(ph.getName(), StrUtil.nullToEmpty(ph.getValue()));
                }
            }
        }
        return headers;
    }

    /** JSONObject → Map&lt;String,String&gt;（值取字符串形式）；空对象返回空 Map。 */
    private Map<String, String> toStringMap(JSONObject obj) {
        Map<String, String> map = new LinkedHashMap<>();
        if (obj != null) {
            for (String key : obj.keySet()) {
                map.put(key, obj.getString(key));
            }
        }
        return map;
    }

    /**
     * 测试 MCP 远程连接：构建客户端并尝试 {@link McpClientWrapper#initialize()} 初始化。
     * <p>
     * 输入直接使用用户输入框中的 {@link McpTestConnectionParamDTO}，而非持久化的 ToolDTO；
     * 测试仅尝试验证 MCP 服务器是否可达、握手是否成功，不阻塞后续 Agent 装配流程。
     * 无论成功或失败均返回 {@link McpTestConnectionResultDTO}，前端据此展示结果。
     *
     * @param param 用户输入的 MCP 测试连接参数
     * @return 测试结果（success + message + errorType + stackTrace）
     */
    public McpTestConnectionResultDTO testConnection(McpTestConnectionParamDTO param) {
        Assert.notNull(param, "测试连接参数不能为空");
        Assert.notBlank(param.getMcpConfig(), "MCP 配置不能为空");

        // 1. 构造一个临时的 ToolDTO 用于复用 buildMcpClient 逻辑
        ToolDTO tool = new ToolDTO();
        tool.setType(ToolType.MCP.name());
        tool.setCreationMode(CreationMode.REMOTE.name());
        tool.setMcpConfigType(param.getMcpConfigType());
        tool.setMcpConfig(param.getMcpConfig());
        tool.setProxyEnabled(param.getProxyEnabled());
        tool.setProxyHeaders(param.getProxyHeaders());
        tool.setNum("_test_");
        tool.setName("_test_");

        try {
            // 2. 构建 MCP 客户端（同 AgentRunnerFactory 的装配逻辑）
            McpClientWrapper client = buildMcpClient(tool, Collections.emptyMap());
            if (client == null) {
                return McpTestConnectionResultDTO.builder()
                        .success(false)
                        .message("无法构建 MCP 客户端，请检查配置参数")
                        .errorType("BUILD_FAILED")
                        .build();
            }

            // 3. 尝试初始化连接
            McpTestConnectionResultDTO result = client.initialize()
                    .then(Mono.fromCallable(() -> {
                        // 初始化成功后再尝试 listTools 确认连通性
                        return client.listTools()
                                .flatMap(tools -> {
                                    int count = tools != null ? tools.size() : 0;
                                    String msg = "连接成功！" + (count > 0
                                            ? "发现 " + count + " 个工具"
                                            : "未发现任何工具");
                                    return Mono.just(McpTestConnectionResultDTO.builder()
                                            .success(true)
                                            .message(msg)
                                            .build());
                                })
                                .onErrorResume(e -> {
                                    // listTools 失败但客户端已连接：视为基本连通
                                    log.warn("[mcp-test-connection] listTools failed after init, treat as partial ok", e);
                                    return Mono.just(McpTestConnectionResultDTO.builder()
                                            .success(true)
                                            .message("连接成功（但列出工具失败：" + e.getMessage() + "）")
                                            .build());
                                });
                    }))
                    .flatMap(m -> m)
                    .block(Duration.ofSeconds(MCP_TIMEOUT_SECONDS));

            return result != null ? result : McpTestConnectionResultDTO.builder()
                    .success(false)
                    .message("测试连接超时，请检查网络或 MCP 服务器地址")
                    .errorType("TIMEOUT")
                    .build();

        } catch (Exception e) {
            // 异常捕获，返回详细错误信息
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            if (stackTrace.length() > 1000) {
                stackTrace = stackTrace.substring(0, 1000);
            }

            String errorType = determineErrorType(e);
            String message = e.getMessage();
            if (message != null && message.length() > 500) {
                message = message.substring(0, 500);
            }

            log.warn("[mcp-test-connection] failed, errorType={}, message={}", errorType, message);
            return McpTestConnectionResultDTO.builder()
                    .success(false)
                    .message(message != null ? message : "未知错误")
                    .errorType(errorType)
                    .stackTrace(stackTrace)
                    .build();
        } finally {
            // 清理：确保资源释放
            // client 在 initialize.close() 时已被 close；异常路径可能未关闭，但框架会 GC 回收
        }
    }

    /** 根据异常类型判定错误分类。 */
    private static String determineErrorType(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "UNKNOWN";
        }
        String lower = msg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "TIMEOUT";
        } else if (lower.contains("connection refused") || lower.contains("connect refused")) {
            return "CONNECTION_REFUSED";
        } else if (lower.contains("dns") || lower.contains("unknown host") || lower.contains("unresolved")) {
            return "DNS_RESOLUTION_FAILED";
        } else if (lower.contains("unauthorized") || lower.contains("403") || lower.contains("401")) {
            return "AUTH_FAILED";
        } else if (lower.contains("ssl") || lower.contains("handshake") || lower.contains("certificate")) {
            return "SSL_ERROR";
        } else if (lower.contains("parse") || lower.contains("json") || lower.contains("syntax")) {
            return "PARSE_ERROR";
        }
        return "CONNECTION_FAILED";
    }

    /** MCP 客户端名：工具名规整后的合法标识；为空退化为工具编号。 */
    private String mcpClientName(ToolDTO tool) {
        String base = StrUtil.strip(
                ILLEGAL_NAME_CHARS.matcher(StrUtil.nullToEmpty(tool.getName())).replaceAll("_"), "_");
        return StrUtil.isBlank(base) ? tool.getNum() : base;
    }

    /**
     * 装配端点的 JSON Schema 参数对象（{@code {type:object, properties:{...}, required:[...]}}）。
     * <p>path 参数恒为必填（URL 占位必须填充）；query 参数无默认值时必填。请求头不暴露给 LLM
     * （属配置/鉴权语义，由运行时按默认值注入），不计入参数。
     */
    private Map<String, Object> buildParameters(ApiEndpointDTO endpoint) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (CollUtil.isNotEmpty(endpoint.getPathParams())) {
            for (ApiParamDTO param : endpoint.getPathParams()) {
                properties.put(param.getName(), propertySchema(param));
                required.add(param.getName());
            }
        }
        if (CollUtil.isNotEmpty(endpoint.getQueryParams())) {
            for (ApiParamDTO param : endpoint.getQueryParams()) {
                properties.put(param.getName(), propertySchema(param));
                if (StrUtil.isBlank(param.getDefaultValue())) {
                    required.add(param.getName());
                }
            }
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);
        return parameters;
    }

    /** 单个参数 → JSON Schema 属性节点（type + description，带默认值时附 default）。 */
    private Map<String, Object> propertySchema(ApiParamDTO param) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", jsonType(param.getType()));
        if (StrUtil.isNotBlank(param.getDescription())) {
            node.put("description", param.getDescription());
        }
        if (StrUtil.isNotBlank(param.getDefaultValue())) {
            node.put("default", param.getDefaultValue());
        }
        return node;
    }

    /** API 参数类型名（{@code ApiParamType} 枚举名）→ JSON Schema 基础类型；未知兜底 string。 */
    private String jsonType(String type) {
        if (StrUtil.isBlank(type)) {
            return "string";
        }
        return switch (type) {
            case "NUMBER" -> "number";
            case "BOOLEAN" -> "boolean";
            case "INTEGER" -> "integer";
            default -> "string";
        };
    }

    /**
     * 生成端点对应的合法 function name：以「工具名（非 ASCII 时退化为工具编号）+ 方法 + 路径」为基底，
     * 替换非法字符为下划线、折叠重复下划线、截断至 64 字符，保证同一工具内端点间可区分且符合厂商约束。
     */
    private String functionName(ToolDTO tool, ApiEndpointDTO endpoint) {
        String base = ILLEGAL_NAME_CHARS.matcher(StrUtil.nullToEmpty(tool.getName())).replaceAll("_");
        if (StrUtil.isBlank(StrUtil.strip(base, "_"))) {
            base = tool.getNum();
        }
        String method = StrUtil.nullToEmpty(endpoint.getMethod());
        String raw = base + "_" + method + "_" + StrUtil.nullToEmpty(endpoint.getPath());
        String sanitized = ILLEGAL_NAME_CHARS.matcher(raw).replaceAll("_")
                .replaceAll("_{2,}", "_");
        sanitized = StrUtil.strip(sanitized, "_");
        if (sanitized.length() > FUNCTION_NAME_MAX_LENGTH) {
            sanitized = sanitized.substring(0, FUNCTION_NAME_MAX_LENGTH);
            sanitized = StrUtil.strip(sanitized, "_");
        }
        return sanitized;
    }
}
