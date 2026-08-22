package ink.garry.rd.agent.ws.infra.evaluation.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("eval_task_item_score")
public class EvalTaskItemScoreEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("task_item_num")
    private String taskItemNum;
    @TableField("grader_num")
    private String graderNum;
    @TableField("grader_version")
    private Integer graderVersion;
    private BigDecimal score;
    private Boolean passed;
    private String explanation;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
