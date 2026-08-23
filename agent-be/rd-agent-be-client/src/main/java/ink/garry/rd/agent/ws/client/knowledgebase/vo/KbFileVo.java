package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbFileVo {
    private String fileNum;
    private String kbNum;
    private String ossFileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String indexStatus;
    private Integer indexConfigVersion;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime indexedAt;
}
