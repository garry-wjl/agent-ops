package ink.garry.rd.agent.ws.infra.evaluation.grader.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import lombok.Data;

import java.time.LocalDateTime;

/** 评估器表实体。 */
@Data
@TableName("eval_grader")
public class EvalGraderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("workspace_num")
    private String workspaceNum;
    private String name;
    private String description;
    private String kind;
    @TableField("builtin_code")
    private String builtinCode;
    @TableField("config_json")
    private String configJson;
    private Integer version;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static EvalGrader toDomain(EvalGraderEntity e) {
        if (e == null) {
            return null;
        }
        EvalGrader g = new EvalGrader();
        g.setId(e.getId());
        g.setNum(e.getNum());
        g.setWorkspaceNum(e.getWorkspaceNum());
        g.setName(e.getName());
        g.setDescription(e.getDescription());
        g.setKind(e.getKind() == null ? null : GraderKind.valueOf(e.getKind()));
        g.setBuiltinCode(e.getBuiltinCode());
        g.setConfigJson(e.getConfigJson());
        g.setVersion(e.getVersion());
        g.setCreateNo(e.getCreateNo());
        g.setUpdateNo(e.getUpdateNo());
        g.setCreateTime(e.getCreateTime());
        g.setUpdateTime(e.getUpdateTime());
        return g;
    }

    public static EvalGraderEntity fromDomain(EvalGrader g) {
        EvalGraderEntity e = new EvalGraderEntity();
        e.setId(g.getId());
        e.setNum(g.getNum());
        e.setWorkspaceNum(g.getWorkspaceNum());
        e.setName(g.getName());
        e.setDescription(g.getDescription());
        e.setKind(g.getKind() == null ? null : g.getKind().name());
        e.setBuiltinCode(g.getBuiltinCode());
        e.setConfigJson(g.getConfigJson());
        e.setVersion(g.getVersion());
        e.setCreateNo(g.getCreateNo());
        e.setUpdateNo(g.getUpdateNo());
        e.setCreateTime(g.getCreateTime());
        e.setUpdateTime(g.getUpdateTime());
        e.setDeleted(0);
        return e;
    }
}
