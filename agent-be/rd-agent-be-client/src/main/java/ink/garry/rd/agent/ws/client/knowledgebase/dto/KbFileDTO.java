package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文件 DTO。
 */
@Data
@Builder
public class KbFileDTO {

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
