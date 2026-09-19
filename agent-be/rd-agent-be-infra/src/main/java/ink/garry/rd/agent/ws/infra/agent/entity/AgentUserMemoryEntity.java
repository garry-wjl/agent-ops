package ink.garry.rd.agent.ws.infra.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期记忆表。
 */
@Data
@TableName("agent_user_memory")
public class AgentUserMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String num;

    @TableField("workspace_num")
    private String workspaceNum;

    @TableField("agent_num")
    private String agentNum;

    @TableField("user_id")
    private String userId;

    @TableField("memory_md")
    private String memoryMd;

    @TableField("daily_ledger_json")
    private String dailyLedgerJson;

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
