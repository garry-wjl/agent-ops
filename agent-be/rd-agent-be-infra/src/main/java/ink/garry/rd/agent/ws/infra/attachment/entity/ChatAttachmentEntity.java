package ink.garry.rd.agent.ws.infra.attachment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天附件登记表实体。
 */
@Data
@TableName("chat_attachment")
public class ChatAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String num;

    @TableField("workspace_num")
    private String workspaceNum;

    @TableField("file_id")
    private String fileId;

    @TableField("file_name")
    private String fileName;

    @TableField("mime_type")
    private String mimeType;

    @TableField("size_bytes")
    private Long sizeBytes;

    private String kind;

    @TableField("agent_num")
    private String agentNum;

    @TableField("create_no")
    private String createNo;

    @TableField("update_no")
    private String updateNo;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private Integer deleted;
}
