package ink.garry.rd.agent.ws.client.attachment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Invoke 请求中的附件引用（已上传并登记的 fileId）。
 */
@Data
public class AttachmentRefParam {

    /** OSS 对象 ID（登记表 file_id），必填 */
    @NotBlank(message = "fileId 不能为空")
    private String fileId;

    /** 原始文件名（可空，展示用） */
    private String name;

    /** MIME 类型（可空，归一化时按登记表或后缀补齐） */
    private String mimeType;

    /** 字节大小（可空） */
    private Long size;

    /** 种类：image / file（可空，由 mime 推导） */
    private String kind;
}
