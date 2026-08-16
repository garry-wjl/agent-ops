package ink.garry.rd.agent.ws.client.attachment;

import lombok.Builder;
import lombok.Data;

/**
 * 聊天附件登记只读 DTO。
 */
@Data
@Builder
public class ChatAttachmentDTO {

    /** 业务编号 CHA… */
    private String num;

    /** 工作空间编号 */
    private String workspaceNum;

    /** OSS 对象 ID */
    private String fileId;

    /** 原始文件名 */
    private String fileName;

    /** MIME */
    private String mimeType;

    /** 字节大小 */
    private Long sizeBytes;

    /** IMAGE / FILE */
    private String kind;

    /** 关联 Agent，可空 */
    private String agentNum;
}
