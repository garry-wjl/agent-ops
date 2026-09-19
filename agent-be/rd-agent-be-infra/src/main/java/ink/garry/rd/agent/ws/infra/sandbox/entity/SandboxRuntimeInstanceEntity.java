package ink.garry.rd.agent.ws.infra.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙箱运行时实例表实体。
 */
@Data
@TableName("sandbox_runtime_instance")
public class SandboxRuntimeInstanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String num;

    @TableField("sandbox_num")
    private String sandboxNum;

    @TableField("workspace_num")
    private String workspaceNum;

    @TableField("opensandbox_instance_id")
    private String opensandboxInstanceId;

    private String status;

    @TableField("session_num")
    private String sessionNum;

    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    @TableField("idle_since")
    private LocalDateTime idleSince;

    @TableField("create_no")
    private String createNo;

    @TableField("update_no")
    private String updateNo;

    @TableLogic
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
