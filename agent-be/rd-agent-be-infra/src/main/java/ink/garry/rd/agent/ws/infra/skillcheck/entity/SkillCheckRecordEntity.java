package ink.garry.rd.agent.ws.infra.skillcheck.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 发布检测记录持久化实体（对应表 {@code skill_check_record}，v3.0 新增）。
 * <p>
 * 一次发布检测一行；{@code errors} 错误明细列表序列化为 JSON 字符串列（与项目既有
 * tags JSON 列同口径，使用 fastjson2）。
 */
@Data
@TableName("skill_check_record")
public class SkillCheckRecordEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 SCR，由 {@code SkillCheckRecordGateway.generateCheckRecordNum} 生成） */
    private String num;

    /** 所属 Skill 业务编号 */
    @TableField("skill_num")
    private String skillNum;

    /** 检测的目标版本号 */
    private String version;

    /** 整体检测结果 {@code PASS} / {@code FAIL}，以字符串存储 */
    private String result;

    /** 大小检测子结果 {@code PASS} / {@code FAIL} / {@code SKIPPED} */
    @TableField("size_result")
    private String sizeResult;

    /** 格式检测子结果 */
    @TableField("format_result")
    private String formatResult;

    /** 可用性检测子结果 */
    @TableField("availability_result")
    private String availabilityResult;

    /** 错误明细数组；持久化为 JSON 字符串列（TEXT） */
    private String errors;

    /** 检测总耗时（毫秒） */
    @TableField("cost_ms")
    private Long costMs;

    /** 归属工作空间业务编号（前缀 WS-） */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 创建人（触发检测者）userId */
    @TableField("create_no")
    private String createNo;

    /** 更新人 userId */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 检测时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** Entity → Domain；transient 依赖由调用方装配。 */
    public static SkillCheckRecord toDomain(SkillCheckRecordEntity e) {
        if (e == null) {
            return null;
        }
        SkillCheckRecord r = new SkillCheckRecord();
        r.setId(e.getId());
        r.setNum(e.getNum());
        r.setSkillNum(e.getSkillNum());
        r.setVersion(e.getVersion());
        r.setResult(e.getResult() == null ? null : SkillCheckResult.valueOf(e.getResult()));
        r.setSizeResult(parseItem(e.getSizeResult()));
        r.setFormatResult(parseItem(e.getFormatResult()));
        r.setAvailabilityResult(parseItem(e.getAvailabilityResult()));
        r.setErrors(parseErrors(e.getErrors()));
        r.setCostMs(e.getCostMs());
        r.setWorkspaceNum(e.getWorkspaceNum());
        r.setCreateNo(e.getCreateNo());
        r.setUpdateNo(e.getUpdateNo());
        r.setDeleted(e.getDeleted());
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }

    /** Domain → Entity。 */
    public static SkillCheckRecordEntity fromDomain(SkillCheckRecord r) {
        SkillCheckRecordEntity e = new SkillCheckRecordEntity();
        e.setId(r.getId());
        e.setNum(r.getNum());
        e.setSkillNum(r.getSkillNum());
        e.setVersion(r.getVersion());
        e.setResult(r.getResult() == null ? null : r.getResult().name());
        e.setSizeResult(r.getSizeResult() == null ? null : r.getSizeResult().name());
        e.setFormatResult(r.getFormatResult() == null ? null : r.getFormatResult().name());
        e.setAvailabilityResult(r.getAvailabilityResult() == null ? null : r.getAvailabilityResult().name());
        e.setErrors(r.getErrors() == null ? null : JSON.toJSONString(r.getErrors()));
        e.setCostMs(r.getCostMs());
        e.setWorkspaceNum(r.getWorkspaceNum());
        e.setCreateNo(r.getCreateNo());
        e.setUpdateNo(r.getUpdateNo());
        e.setDeleted(r.getDeleted() == null ? 0 : r.getDeleted());
        e.setCreateTime(r.getCreateTime());
        e.setUpdateTime(r.getUpdateTime());
        return e;
    }

    /** 字符串 → SkillCheckItemResult；空值返回 null。 */
    private static SkillCheckItemResult parseItem(String s) {
        return s == null || s.isBlank() ? null : SkillCheckItemResult.valueOf(s);
    }

    /** errors JSON 列 → {@code List<SkillCheckError>}；空值 / 非法 JSON 返回空集合。 */
    private static List<SkillCheckError> parseErrors(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseObject(json, new TypeReference<List<SkillCheckError>>() {});
        } catch (Exception ignore) {
            return new ArrayList<>();
        }
    }
}
