package ink.garry.rd.agent.ws.infra.evaluation.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.ItemStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_task_item")
public class EvalTaskItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("task_num")
    private String taskNum;
    @TableField("row_index")
    private Integer rowIndex;
    @TableField("input_json")
    private String inputJson;
    @TableField("actual_output")
    private String actualOutput;
    @TableField("trace_summary_json")
    private String traceSummaryJson;
    @TableField("overall_pass")
    private Boolean overallPass;
    private String status;
    @TableField("latency_ms")
    private Long latencyMs;
    @TableField("error_message")
    private String errorMessage;
    @TableField("label_json")
    private String labelJson;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static EvalTaskItem toDomain(EvalTaskItemEntity e) {
        if (e == null) {
            return null;
        }
        EvalTaskItem i = new EvalTaskItem();
        i.setId(e.getId());
        i.setNum(e.getNum());
        i.setTaskNum(e.getTaskNum());
        i.setRowIndex(e.getRowIndex());
        i.setInputJson(e.getInputJson());
        i.setActualOutput(e.getActualOutput());
        i.setTraceSummaryJson(e.getTraceSummaryJson());
        i.setOverallPass(e.getOverallPass());
        i.setStatus(e.getStatus() == null ? null : ItemStatus.valueOf(e.getStatus()));
        i.setLatencyMs(e.getLatencyMs());
        i.setErrorMessage(e.getErrorMessage());
        i.setLabelJson(e.getLabelJson());
        i.setCreateNo(e.getCreateNo());
        i.setUpdateNo(e.getUpdateNo());
        i.setCreateTime(e.getCreateTime());
        i.setUpdateTime(e.getUpdateTime());
        return i;
    }

    public static EvalTaskItemEntity fromDomain(EvalTaskItem i) {
        EvalTaskItemEntity e = new EvalTaskItemEntity();
        e.setId(i.getId());
        e.setNum(i.getNum());
        e.setTaskNum(i.getTaskNum());
        e.setRowIndex(i.getRowIndex());
        e.setInputJson(i.getInputJson());
        e.setActualOutput(i.getActualOutput());
        e.setTraceSummaryJson(i.getTraceSummaryJson());
        e.setOverallPass(i.getOverallPass());
        e.setStatus(i.getStatus() == null ? null : i.getStatus().name());
        e.setLatencyMs(i.getLatencyMs());
        e.setErrorMessage(i.getErrorMessage());
        e.setLabelJson(i.getLabelJson());
        e.setCreateNo(i.getCreateNo());
        e.setUpdateNo(i.getUpdateNo());
        e.setCreateTime(i.getCreateTime());
        e.setUpdateTime(i.getUpdateTime());
        e.setDeleted(0);
        return e;
    }
}
