package ink.garry.rd.agent.ws.infra.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvaluationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测任务持久化实体（对应表 evaluation）。
 */
@Data
@TableName("evaluation")
public class EvaluationEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 EVL... */
    private String num;

    /** 评测名称（人类可读） */
    private String name;

    /** 被评测的 Agent num */
    @TableField("agent_num")
    private String agentNum;

    /** 被评测的 Agent 版本 num；可空表示评测最新版本 */
    @TableField("agent_version_num")
    private String agentVersionNum;

    /** 被评测的 Skill num；可空表示对整个 Agent 评测 */
    @TableField("skill_num")
    private String skillNum;

    /** 评测状态 PENDING / RUNNING / FINISHED / FAILED */
    private String status;

    /** 评测发起人 userId */
    @TableField("creator_user_id")
    private String creatorUserId;

    /** 评测用例总数（finish 时回填） */
    @TableField("total_case_count")
    private Integer totalCaseCount;

    /** 评测通过用例数（finish 时回填） */
    @TableField("passed_case_count")
    private Integer passedCaseCount;

    /** 评测失败用例数（finish 时回填） */
    @TableField("failed_case_count")
    private Integer failedCaseCount;

    /** 创建人 userId */
    @TableField("create_no")
    private String createNo;

    /** 更新人 userId */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除 0/1 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** Entity → Domain。 */
    public static Evaluation toDomain(EvaluationEntity e) {
        if (e == null) {
            return null;
        }
        Evaluation v = new Evaluation();
        v.setId(e.getId());
        v.setNum(e.getNum());
        v.setName(e.getName());
        v.setAgentNum(e.getAgentNum());
        v.setAgentVersionNum(e.getAgentVersionNum());
        v.setSkillNum(e.getSkillNum());
        v.setStatus(EvaluationStatus.valueOf(e.getStatus()));
        v.setCreatorUserId(e.getCreatorUserId());
        v.setTotalCaseCount(e.getTotalCaseCount());
        v.setPassedCaseCount(e.getPassedCaseCount());
        v.setFailedCaseCount(e.getFailedCaseCount());
        v.setCreateNo(e.getCreateNo());
        v.setUpdateNo(e.getUpdateNo());
        v.setDeleted(e.getDeleted());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /** Domain → Entity。 */
    public static EvaluationEntity fromDomain(Evaluation v) {
        EvaluationEntity e = new EvaluationEntity();
        e.setId(v.getId());
        e.setNum(v.getNum());
        e.setName(v.getName());
        e.setAgentNum(v.getAgentNum());
        e.setAgentVersionNum(v.getAgentVersionNum());
        e.setSkillNum(v.getSkillNum());
        e.setStatus(v.getStatus().name());
        e.setCreatorUserId(v.getCreatorUserId());
        e.setTotalCaseCount(v.getTotalCaseCount());
        e.setPassedCaseCount(v.getPassedCaseCount());
        e.setFailedCaseCount(v.getFailedCaseCount());
        e.setCreateNo(v.getCreateNo());
        e.setUpdateNo(v.getUpdateNo());
        e.setDeleted(v.getDeleted() == null ? 0 : v.getDeleted());
        e.setCreateTime(v.getCreateTime());
        e.setUpdateTime(v.getUpdateTime());
        return e;
    }
}
