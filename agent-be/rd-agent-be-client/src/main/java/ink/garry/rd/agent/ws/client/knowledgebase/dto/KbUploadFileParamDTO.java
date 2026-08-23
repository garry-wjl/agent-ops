package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

/**
 * 登记已上传文件入参 DTO。
 */
@Data
public class KbUploadFileParamDTO {

    private String kbNum;
    private String ossFileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
}
