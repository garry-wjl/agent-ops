package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

import java.util.List;

/**
 * 端点元数据出参 Vo（adapter 层；对应 EndpointMetaDTO）。
 * <p>发布 OpenAPI 形态工具时解析得到，供列表展示端点数与详情预览。
 */
@Data
public class EndpointMetaVo {

    /** 识别到的端点数量。 */
    private Integer endpointCount;

    /** 端点摘要列表。 */
    private List<EndpointSummaryVo> summaries;

    /**
     * 端点摘要 Vo（对应 EndpointMetaDTO.EndpointSummaryDTO）。
     */
    @Data
    public static class EndpointSummaryVo {

        /** 端点路径。 */
        private String path;

        /** HTTP 方法（大写）。 */
        private String method;

        /** 端点描述。 */
        private String summary;
    }
}
