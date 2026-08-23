package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 检索结果 chunk DTO。
 */
@Data
@Builder
public class KbChunkDTO {

    private String kbNum;
    private String fileNum;
    private String fileName;
    private Integer configVersion;
    private String content;
    private Double score;
}
