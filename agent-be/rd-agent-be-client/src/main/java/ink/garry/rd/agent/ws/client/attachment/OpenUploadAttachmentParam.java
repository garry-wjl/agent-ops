package ink.garry.rd.agent.ws.client.attachment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 开放接口上传附件预签名入参。
 */
@Data
public class OpenUploadAttachmentParam {

    /** 目标 Agent 业务编号，须与 API Key 归属一致 */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;

    /** 原始文件名 */
    @NotBlank(message = "fileName 不能为空")
    private String fileName;

    /** MIME 类型 */
    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    /** 文件字节大小 */
    @NotNull(message = "size 不能为空")
    @Positive(message = "size 必须为正数")
    private Long size;
}
