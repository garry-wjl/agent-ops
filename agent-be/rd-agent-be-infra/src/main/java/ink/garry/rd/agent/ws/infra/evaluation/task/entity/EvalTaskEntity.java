package ink.garry.rd.agent.ws.infra.evaluation.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_task")
public class EvalTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("workspace_num")
    private String workspaceNum;
    private String name;
    private String description;
    @TableField("dataset_num")
    private String datasetNum;
    @TableField("dataset_version")
    private Integer datasetVersion;
    @TableField("bind_mode")
    private String bindMode;
    @TableField("agent_num")
    private String agentNum;
    @TableField("agent_version_num")
    private String agentVersionNum;
    @TableField("grader_bindings_json")
    private String graderBindingsJson;
    @TableField("label_config_json")
    private String labelConfigJson;
    private String status;
    @TableField("total_count")
    private Integer totalCount;
    @TableField("passed_count")
    private Integer passedCount;
    @TableField("failed_count")
    private Integer failedCount;
    @TableField("creator_user_id")
    private String creatorUserId;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static EvalTask toDomain(EvalTaskEntity e) {
        if (e == null) {
            return null;
        }
        EvalTask t = new EvalTask();
        t.setId(e.getId());
        t.setNum(e.getNum());
        t.setWorkspaceNum(e.getWorkspaceNum());
        t.setName(e.getName());
        t.setDescription(e.getDescription());
        t.setDatasetNum(e.getDatasetNum());
        t.setDatasetVersion(e.getDatasetVersion());
        t.setBindMode(e.getBindMode() == null ? null : BindMode.valueOf(e.getBindMode()));
        t.setAgentNum(e.getAgentNum());
        t.setAgentVersionNum(e.getAgentVersionNum());
        t.setGraderBindingsJson(e.getGraderBindingsJson());
        t.setLabelConfigJson(e.getLabelConfigJson());
        t.setStatus(e.getStatus() == null ? null : TaskStatus.valueOf(e.getStatus()));
        t.setTotalCount(e.getTotalCount());
        t.setPassedCount(e.getPassedCount());
        t.setFailedCount(e.getFailedCount());
        t.setCreatorUserId(e.getCreatorUserId());
        t.setCreateNo(e.getCreateNo());
        t.setUpdateNo(e.getUpdateNo());
        t.setCreateTime(e.getCreateTime());
        t.setUpdateTime(e.getUpdateTime());
        return t;
    }

    public static EvalTaskEntity fromDomain(EvalTask t) {
        EvalTaskEntity e = new EvalTaskEntity();
        e.setId(t.getId());
        e.setNum(t.getNum());
        e.setWorkspaceNum(t.getWorkspaceNum());
        e.setName(t.getName());
        e.setDescription(t.getDescription());
        e.setDatasetNum(t.getDatasetNum());
        e.setDatasetVersion(t.getDatasetVersion());
        e.setBindMode(t.getBindMode() == null ? null : t.getBindMode().name());
        e.setAgentNum(t.getAgentNum());
        e.setAgentVersionNum(t.getAgentVersionNum());
        e.setGraderBindingsJson(t.getGraderBindingsJson());
        e.setLabelConfigJson(t.getLabelConfigJson());
        e.setStatus(t.getStatus() == null ? null : t.getStatus().name());
        e.setTotalCount(t.getTotalCount());
        e.setPassedCount(t.getPassedCount());
        e.setFailedCount(t.getFailedCount());
        e.setCreatorUserId(t.getCreatorUserId());
        e.setCreateNo(t.getCreateNo());
        e.setUpdateNo(t.getUpdateNo());
        e.setCreateTime(t.getCreateTime());
        e.setUpdateTime(t.getUpdateTime());
        e.setDeleted(0);
        return e;
    }
}
