package ink.garry.rd.agent.ws.infra.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评测黄金集种子持久化实体（对应表 eval_seed）。
 */
@Data
@TableName("eval_seed")
public class EvalSeedEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 ESD... */
    private String num;

    /** 关联的 Skill num */
    @TableField("skill_num")
    private String skillNum;

    /** 种子输入文本（标准化的提示或测试输入） */
    private String input;

    /** 期望输出文本，作为 Judge 评分参考标准 */
    @TableField("expected_output")
    private String expectedOutput;

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
    public static EvalSeed toDomain(EvalSeedEntity e) {
        if (e == null) {
            return null;
        }
        EvalSeed s = new EvalSeed();
        s.setId(e.getId());
        s.setNum(e.getNum());
        s.setSkillNum(e.getSkillNum());
        s.setInput(e.getInput());
        s.setExpectedOutput(e.getExpectedOutput());
        s.setCreateNo(e.getCreateNo());
        s.setUpdateNo(e.getUpdateNo());
        s.setDeleted(e.getDeleted());
        s.setCreateTime(e.getCreateTime());
        s.setUpdateTime(e.getUpdateTime());
        return s;
    }

    /** Domain → Entity。 */
    public static EvalSeedEntity fromDomain(EvalSeed s) {
        EvalSeedEntity e = new EvalSeedEntity();
        e.setId(s.getId());
        e.setNum(s.getNum());
        e.setSkillNum(s.getSkillNum());
        e.setInput(s.getInput());
        e.setExpectedOutput(s.getExpectedOutput());
        e.setCreateNo(s.getCreateNo());
        e.setUpdateNo(s.getUpdateNo());
        e.setDeleted(s.getDeleted() == null ? 0 : s.getDeleted());
        e.setCreateTime(s.getCreateTime());
        e.setUpdateTime(s.getUpdateTime());
        return e;
    }
}
