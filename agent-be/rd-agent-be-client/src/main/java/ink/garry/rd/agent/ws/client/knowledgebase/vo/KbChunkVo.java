package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

@Data
public class KbChunkVo {
    private String kbNum;
    private String fileNum;
    private String fileName;
    private Integer configVersion;
    private String content;
    private Double score;
}
