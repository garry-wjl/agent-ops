package ink.garry.rd.agent.ws.infra.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvalCaseStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测执行用例持久化实体（对应表 evaluation_case）。
 */
@Data
@TableName("evaluation_case")
public class EvaluationCaseEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 EVC... */
    private String num;

    /** 所属评测的业务编号（外键关联 evaluation.num） */
    @TableField("evaluation_num")
    private String evaluationNum;

    /** 用例输入文本 */
    private String input;

    /** 用例期望输出（来自种子或人工编辑） */
    @TableField("expected_output")
    private String expectedOutput;

    /** 实际输出（执行后回填） */
    @TableField("actual_output")
    private String actualOutput;

    /** Judge 评分结果 JSON/文本（执行后回填） */
    @TableField("judge_result")
    private String judgeResult;

    /** 用例状态 PENDING / RUNNING / PASSED / FAILED */
    private String status;

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
    public static EvaluationCase toDomain(EvaluationCaseEntity e) {
        if (e == null) {
            return null;
        }
        EvaluationCase c = new EvaluationCase();
        c.setId(e.getId());
        c.setNum(e.getNum());
        c.setEvaluationNum(e.getEvaluationNum());
        c.setInput(e.getInput());
        c.setExpectedOutput(e.getExpectedOutput());
        c.setActualOutput(e.getActualOutput());
        c.setJudgeResult(e.getJudgeResult());
        c.setStatus(EvalCaseStatus.valueOf(e.getStatus()));
        c.setCreateNo(e.getCreateNo());
        c.setUpdateNo(e.getUpdateNo());
        c.setDeleted(e.getDeleted());
        c.setCreateTime(e.getCreateTime());
        c.setUpdateTime(e.getUpdateTime());
        return c;
    }

    /** Domain → Entity。 */
    public static EvaluationCaseEntity fromDomain(EvaluationCase c) {
        EvaluationCaseEntity e = new EvaluationCaseEntity();
        e.setId(c.getId());
        e.setNum(c.getNum());
        e.setEvaluationNum(c.getEvaluationNum());
        e.setInput(c.getInput());
        e.setExpectedOutput(c.getExpectedOutput());
        e.setActualOutput(c.getActualOutput());
        e.setJudgeResult(c.getJudgeResult());
        e.setStatus(c.getStatus().name());
        e.setCreateNo(c.getCreateNo());
        e.setUpdateNo(c.getUpdateNo());
        e.setDeleted(c.getDeleted() == null ? 0 : c.getDeleted());
        e.setCreateTime(c.getCreateTime());
        e.setUpdateTime(c.getUpdateTime());
        return e;
    }
}
