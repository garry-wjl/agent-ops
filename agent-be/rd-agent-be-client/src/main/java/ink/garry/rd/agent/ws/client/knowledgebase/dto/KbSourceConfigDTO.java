package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库底层资源配置 DTO。
 */
@Data
@Builder
public class KbSourceConfigDTO {

    private String vectorCollectionName;
    private Integer dimensions;
    private String ragFlowDatasetId;
}
