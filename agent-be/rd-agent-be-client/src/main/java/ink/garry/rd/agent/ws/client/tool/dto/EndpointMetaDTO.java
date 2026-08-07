package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 端点元数据 DTO（application 层边界；对应 domain EndpointMeta 值对象）。
 * <p>发布 OpenAPI 形态工具时解析得到，供列表展示端点数与详情预览。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EndpointMetaDTO {

    /** 识别到的端点数量。 */
    private Integer endpointCount;

    /** 端点摘要列表。 */
    private List<EndpointSummaryDTO> summaries;

    /**
     * 端点摘要 DTO（对应 domain EndpointSummary 值对象）。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EndpointSummaryDTO {

        /** 端点路径。 */
        private String path;

        /** HTTP 方法（大写）。 */
        private String method;

        /** 端点描述。 */
        private String summary;
    }
}
