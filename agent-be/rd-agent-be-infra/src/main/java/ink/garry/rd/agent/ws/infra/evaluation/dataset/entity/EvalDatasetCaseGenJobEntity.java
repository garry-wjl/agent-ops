package ink.garry.rd.agent.ws.infra.evaluation.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测集自动生成 Case 任务实体。 */
@Data
@TableName("eval_dataset_case_gen_job")
public class EvalDatasetCaseGenJobEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("workspace_num")
    private String workspaceNum;
    @TableField("dataset_num")
    private String datasetNum;
    @TableField("generator_agent_num")
    private String generatorAgentNum;
    @TableField("generator_agent_version_num")
    private String generatorAgentVersionNum;
    @TableField("target_count")
    private Integer targetCount;
    @TableField("clear_draft")
    private Boolean clearDraft;
    @TableField("instruction_mode")
    private String instructionMode;
    @TableField("user_instruction")
    private String userInstruction;
    private String status;
    @TableField("progress_pct")
    private Integer progressPct;
    @TableField("progress_message")
    private String progressMessage;
    @TableField("parsed_count")
    private Integer parsedCount;
    @TableField("written_count")
    private Integer writtenCount;
    @TableField("skipped_count")
    private Integer skippedCount;
    @TableField("error_message")
    private String errorMessage;
    @TableField("raw_output")
    private String rawOutput;
    @TableField("prompt_snapshot")
    private String promptSnapshot;
    @TableField("retry_of_num")
    private String retryOfNum;
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
