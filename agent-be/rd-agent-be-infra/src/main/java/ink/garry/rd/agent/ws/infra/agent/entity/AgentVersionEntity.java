package ink.garry.rd.agent.ws.infra.agent.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.agent.AgentVersion;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentVersionStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 版本快照持久化实体（对应表 agent_version）。
 * <p>
 * v3.0：新增 {@code status / editor_user_id / lock_until} 列；version_num / change_level /
 * remark / published_by / published_at / semver_* 改为可空（DRAFT 行为 NULL）。
 */
@Data
@TableName("agent_version")
public class AgentVersionEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 版本业务编号 AVN... */
    private String num;

    /** 关联 Agent 业务编号 */
    @TableField("agent_num")
    private String agentNum;

    /**
     * v3.0：版本状态 DRAFT / PUBLISHED / ARCHIVED。
     * <p>
     * DRAFT：当前可编辑草稿；PUBLISHED：当前在线（current_flag=1）；ARCHIVED：历史已发布。
     */
    private String status;

    /** 版本号字符串（vX.Y.Z）；DRAFT 时为 NULL */
    @TableField("version_num")
    private String versionNum;

    /** Semver major；DRAFT 时为 NULL */
    @TableField("semver_major")
    private Integer semverMajor;

    /** Semver minor；DRAFT 时为 NULL */
    @TableField("semver_minor")
    private Integer semverMinor;

    /** Semver patch；DRAFT 时为 NULL */
    @TableField("semver_patch")
    private Integer semverPatch;

    /** JSON 列；存 ConfigSnapshot 序列化字符串（DRAFT 行也必填） */
    @TableField("config_snapshot")
    private String configSnapshot;

    /** 发布备注（≥10 字符）；DRAFT 时为 NULL */
    private String remark;

    /** 发布人 userId；DRAFT 时为 NULL */
    @TableField("published_by")
    private String publishedBy;

    /** 发布时间；DRAFT 时为 NULL */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 当前在线版本标记 0/1；仅 PUBLISHED 行为 1 */
    @TableField("current_flag")
    private Integer currentFlag;

    /** v3.0：当前编辑者 userId（仅 DRAFT 行使用） */
    @TableField("editor_user_id")
    private String editorUserId;

    /** v3.0：草稿编辑锁过期时间（仅 DRAFT 行使用） */
    @TableField("lock_until")
    private LocalDateTime lockUntil;

    /** 创建人 userId */
    @TableField("create_no")
    private String createNo;

    /** 更新人 userId */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除标记 0/1 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain（不装配 transient 依赖）。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域版本实体；e 为 null 返回 null
     */
    public static AgentVersion toDomain(AgentVersionEntity e) {
        if (e == null) {
            return null;
        }
        AgentVersion v = new AgentVersion();
        v.setId(e.getId());
        v.setNum(e.getNum());
        v.setAgentNum(e.getAgentNum());
        v.setStatus(e.getStatus() == null ? AgentVersionStatus.PUBLISHED : AgentVersionStatus.valueOf(e.getStatus()));
        v.setVersionNum(e.getVersionNum());
        if (e.getSemverMajor() != null && e.getSemverMinor() != null && e.getSemverPatch() != null) {
            v.setVersion(new Version(e.getSemverMajor(), e.getSemverMinor(), e.getSemverPatch()));
        }
        v.setConfigSnapshot(JSON.parseObject(e.getConfigSnapshot(), ConfigSnapshot.class));
        v.setRemark(e.getRemark());
        v.setPublishedBy(e.getPublishedBy());
        v.setPublishedAt(e.getPublishedAt());
        v.setCurrent(e.getCurrentFlag() != null && e.getCurrentFlag() == 1);
        v.setEditorUserId(e.getEditorUserId());
        v.setLockUntil(e.getLockUntil());
        v.setCreateNo(e.getCreateNo());
        v.setUpdateNo(e.getUpdateNo());
        v.setDeleted(e.getDeleted());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /**
     * Domain → Entity，准备写入 DB。
     *
     * @param v 领域版本实体
     * @return MyBatis 持久化实体
     */
    public static AgentVersionEntity fromDomain(AgentVersion v) {
        AgentVersionEntity e = new AgentVersionEntity();
        e.setId(v.getId());
        e.setNum(v.getNum());
        e.setAgentNum(v.getAgentNum());
        e.setStatus(v.getStatus() == null ? null : v.getStatus().name());
        e.setVersionNum(v.getVersionNum());
        if (v.getVersion() != null) {
            e.setSemverMajor(v.getVersion().getMajor());
            e.setSemverMinor(v.getVersion().getMinor());
            e.setSemverPatch(v.getVersion().getPatch());
        }
        e.setConfigSnapshot(JSON.toJSONString(v.getConfigSnapshot()));
        e.setRemark(v.getRemark());
        e.setPublishedBy(v.getPublishedBy());
        e.setPublishedAt(v.getPublishedAt());
        e.setCurrentFlag(v.isCurrent() ? 1 : 0);
        e.setEditorUserId(v.getEditorUserId());
        e.setLockUntil(v.getLockUntil());
        e.setCreateNo(v.getCreateNo());
        e.setUpdateNo(v.getUpdateNo());
        e.setDeleted(v.getDeleted() == null ? 0 : v.getDeleted());
        e.setCreateTime(v.getCreateTime());
        e.setUpdateTime(v.getUpdateTime());
        return e;
    }
}
