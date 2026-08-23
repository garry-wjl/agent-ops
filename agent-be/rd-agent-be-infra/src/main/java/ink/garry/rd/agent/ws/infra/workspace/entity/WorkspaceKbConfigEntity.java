package ink.garry.rd.agent.ws.infra.workspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作空间知识库配置实体。
 */
@Data
@TableName("workspace_kb_config")
public class WorkspaceKbConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("workspace_num")
    private String workspaceNum;
    @TableField("config_json")
    private String configJson;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
