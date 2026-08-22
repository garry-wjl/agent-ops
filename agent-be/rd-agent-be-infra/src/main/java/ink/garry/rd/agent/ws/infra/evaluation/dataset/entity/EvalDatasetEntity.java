package ink.garry.rd.agent.ws.infra.evaluation.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetStatus;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测集表实体。 */
@Data
@TableName("eval_dataset")
public class EvalDatasetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("workspace_num")
    private String workspaceNum;
    private String name;
    private String description;
    private String type;
    @TableField("agent_num")
    private String agentNum;
    @TableField("schema_json")
    private String schemaJson;
    private String status;
    @TableField("latest_version")
    private Integer latestVersion;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static EvalDataset toDomain(EvalDatasetEntity e) {
        if (e == null) {
            return null;
        }
        EvalDataset d = new EvalDataset();
        d.setId(e.getId());
        d.setNum(e.getNum());
        d.setWorkspaceNum(e.getWorkspaceNum());
        d.setName(e.getName());
        d.setDescription(e.getDescription());
        d.setType(e.getType() == null ? null : DatasetType.valueOf(e.getType()));
        d.setAgentNum(e.getAgentNum());
        d.setSchemaJson(e.getSchemaJson());
        d.setStatus(e.getStatus() == null ? null : DatasetStatus.valueOf(e.getStatus()));
        d.setLatestVersion(e.getLatestVersion());
        d.setCreateNo(e.getCreateNo());
        d.setUpdateNo(e.getUpdateNo());
        d.setCreateTime(e.getCreateTime());
        d.setUpdateTime(e.getUpdateTime());
        return d;
    }

    public static EvalDatasetEntity fromDomain(EvalDataset d) {
        EvalDatasetEntity e = new EvalDatasetEntity();
        e.setId(d.getId());
        e.setNum(d.getNum());
        e.setWorkspaceNum(d.getWorkspaceNum());
        e.setName(d.getName());
        e.setDescription(d.getDescription());
        e.setType(d.getType() == null ? null : d.getType().name());
        e.setAgentNum(d.getAgentNum());
        e.setSchemaJson(d.getSchemaJson());
        e.setStatus(d.getStatus() == null ? null : d.getStatus().name());
        e.setLatestVersion(d.getLatestVersion());
        e.setCreateNo(d.getCreateNo());
        e.setUpdateNo(d.getUpdateNo());
        e.setCreateTime(d.getCreateTime());
        e.setUpdateTime(d.getUpdateTime());
        e.setDeleted(0);
        return e;
    }
}
