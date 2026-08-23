package ink.garry.rd.agent.ws.domain.knowledgebase.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 知识库底层资源配置。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSourceConfig {

    private String vectorCollectionName;
    private Integer dimensions;
    private String ragFlowDatasetId;
}
