package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 配置版本对齐统计 DTO。
 */
@Data
@Builder
public class KbConfigAlignmentDTO {

    private Integer configVersion;
    private Integer fileCount;
    private Boolean current;
}
