package ink.garry.rd.agent.ws.infra.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 {@code agent_team_run}。
 */
@Data
@TableName("agent_team_run")
public class AgentTeamRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("team_num")
    private String teamNum;
    @TableField("workspace_num")
    private String workspaceNum;
    @TableField("team_name")
    private String teamName;
    private String mode;
    @TableField("input_text")
    private String inputText;
    private String status;
    @TableField("oss_sub_path")
    private String ossSubPath;
    @TableField("snapshot_json")
    private String snapshotJson;
    @TableField("runtime_json")
    private String runtimeJson;
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
