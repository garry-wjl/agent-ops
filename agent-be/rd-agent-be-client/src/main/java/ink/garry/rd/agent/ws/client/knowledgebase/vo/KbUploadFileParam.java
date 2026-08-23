package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbUploadFileParam {
    @NotBlank
    private String kbNum;
    @NotBlank
    private String ossFileId;
    @NotBlank
    private String fileName;
    private String mimeType;
    private Long fileSize;
}
