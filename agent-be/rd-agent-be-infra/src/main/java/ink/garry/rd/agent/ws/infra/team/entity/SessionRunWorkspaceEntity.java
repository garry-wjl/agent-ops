package ink.garry.rd.agent.ws.infra.team.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 {@code session_run_workspace}。主键是会话号。
 */
@Data
@TableName("session_run_workspace")
public class SessionRunWorkspaceEntity {

    @TableId("session_num")
    private String sessionNum;
    @TableField("run_num")
    private String runNum;
    @TableField("sub_path")
    private String subPath;
    @TableField("create_time")
    private LocalDateTime createTime;
}
