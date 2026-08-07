package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 端点元数据值对象（贫血模型）。
 * <p>
 * 发布 OpenAPI 形态工具（{@link CreationMode#OPENAPI_SPEC} 或
 * {@link CreationMode#API_PACKAGE} + {@link PackageMode#OPENAPI_PASTE}）时，
 * 由 {@code ToolGateway.parseOpenApi} 解析 {@code openApiSpec} 得到，落库供列表展示端点数与详情预览。
 * 原始文档仍保留在 {@code openApiSpec} 字段。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EndpointMeta {

    /** 识别到的端点数量。 */
    private Integer endpointCount;

    /** 端点摘要列表。 */
    private List<EndpointSummary> summaries;
}
