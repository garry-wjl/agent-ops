package ink.garry.rd.agent.ws.infra.tool.gateway;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.domain.tool.gateway.ToolGateway;
import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointMeta;
import ink.garry.rd.agent.ws.domain.tool.valueobject.EndpointSummary;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link ToolGateway} 实现：生成工具业务编号 + 解析 OpenAPI 端点元数据。
 * <p>
 * 业务编号复用统一的 {@link BizNumGenerator}（按总体方案 §10.3）：MCP→{@code MCP+yyyyMMddHHmm+4 位序号}，
 * FUNCTION_CALL→{@code FC+...}，与 {@code SkillGatewayImpl}（SKL）/ {@code SandboxGatewayImpl}（SBX）
 * 同风格，便于日志检索与跨域识别。
 * <p>
 * OpenAPI 解析用 fastjson2（与 ToolEntity JSON 列处理同库）：从 {@code paths} 节点提取每个
 * path + HTTP method 的 summary / description 作为端点摘要，供列表展示端点数与详情预览。
 * 业务码 7004 对应"OpenAPI 非法"。
 */
@Slf4j
@Component
public class ToolGatewayImpl implements ToolGateway {

    /** MCP 工具业务编号前缀。 */
    private static final String PREFIX_MCP = "MCP";
    /** FunctionCall 工具业务编号前缀。 */
    private static final String PREFIX_FC = "FC";

    /** OpenAPI 中合法的 HTTP operation key（其余键如 parameters / summary 不计为端点）。 */
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

    @Resource
    private BizNumGenerator bizNumGenerator;

    /**
     * 生成工具业务编号，前缀按类型区分。
     *
     * @param type 工具类型
     * @return 形如 {@code MCP202606091530 0001} / {@code FC202606091530 0001}
     */
    @Override
    public String generateToolNum(ToolType type) {
        String prefix = type == ToolType.MCP ? PREFIX_MCP : PREFIX_FC;
        return bizNumGenerator.generate(prefix);
    }

    /**
     * 解析 OpenAPI / Swagger 文档为端点元数据。
     * <p>
     * 遍历 {@code paths} 下每个 path 的每个 HTTP method operation，提取 summary（缺省取 description）
     * 作为端点摘要。前端已做格式校验（JSON 合法 + 版本字段 + paths 非空），此处再兜底解析；
     * 解析异常或结构不符统一抛业务异常 7004。
     *
     * @param openApiSpec OpenAPI 3.x / Swagger 2.0 JSON 原文
     * @return 端点元数据（端点数 + 摘要列表）
     * @throws BusinessException JSON 非法或缺少 paths 时（code 7004）
     */
    @Override
    public EndpointMeta parseOpenApi(String openApiSpec) {
        JSONObject root;
        try {
            root = JSONObject.parseObject(openApiSpec);
        } catch (JSONException e) {
            log.warn("parseOpenApi JSON 非法: {}", e.getMessage());
            throw new BusinessException(7004, "OpenAPI 文档 JSON 格式错误");
        }
        if (root == null) {
            throw new BusinessException(7004, "OpenAPI 文档为空");
        }
        JSONObject paths = root.getJSONObject("paths");
        if (paths == null || paths.isEmpty()) {
            throw new BusinessException(7004, "OpenAPI 文档缺少 paths 或至少一个端点");
        }

        List<EndpointSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            // 单个 path 对象：key 为 HTTP method（get/post/...），value 为 operation 对象
            JSONObject pathItem = castToJsonObject(pathEntry.getValue());
            if (pathItem == null) {
                continue;
            }
            for (Map.Entry<String, Object> opEntry : pathItem.entrySet()) {
                String method = opEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    // 跳过 parameters / $ref / summary 等非 operation 键
                    continue;
                }
                JSONObject operation = castToJsonObject(opEntry.getValue());
                String summary = operation == null ? null : firstNonBlank(
                        operation.getString("summary"), operation.getString("description"));
                summaries.add(EndpointSummary.builder()
                        .path(path)
                        .method(method.toUpperCase(Locale.ROOT))
                        .summary(summary)
                        .build());
            }
        }

        return EndpointMeta.builder()
                .endpointCount(summaries.size())
                .summaries(summaries)
                .build();
    }

    /** 安全地把 fastjson2 节点转为 JSONObject；非对象结构返回 null。 */
    private JSONObject castToJsonObject(Object value) {
        return value instanceof JSONObject obj ? obj : null;
    }

    /** 返回第一个非空白字符串；都为空白返回 null。 */
    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
