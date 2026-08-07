package ink.garry.rd.agent.ws.domain.tool.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 端点摘要值对象（贫血模型）。
 * <p>
 * 发布时由 {@code ToolGateway.parseOpenApi} 从 OpenAPI/Swagger 文档解析得到的单端点摘要，
 * 用于列表 / 详情展示，不承载运行时调用细节。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EndpointSummary {

    /** 端点路径（如 {@code /users/{id}}）。 */
    private String path;

    /** HTTP 方法（大写字符串，如 GET / POST；来源 OpenAPI operation key）。 */
    private String method;

    /** 端点描述（取 summary 或 description）。 */
    private String summary;
}
