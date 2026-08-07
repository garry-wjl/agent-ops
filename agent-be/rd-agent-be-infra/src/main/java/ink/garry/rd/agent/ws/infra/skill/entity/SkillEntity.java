package ink.garry.rd.agent.ws.infra.skill.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 元信息持久化实体（对应表 {@code skill}）。
 * <p>
 * v3.0 字段集（与 domain {@link Skill} 一一对应）：
 * {@code num / name / description / tags / source / owner_user_id / status / current_version_num} + 审计字段。
 * <p>
 * <b>v3.0 变更</b>：删除 {@code skill_file_key}（OSS key）—— 资源文件树改由 {@code skill_resource_file}
 * 表承载，由 {@code SkillRepositoryImpl} 在 save / findByNum 时级联读写，不在本实体映射。
 * <p>
 * v2.5~v2.7 已删除字段：{@code skill_id} / {@code skill_file_type} / {@code source_version}。
 */
@Data
@TableName("skill")
public class SkillEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 SKL，由 {@code SkillGateway.generateSkillNum} 生成） */
    private String num;

    /** Skill 展示名称（同 ownerUserId 下不可重复，仅 SELF 来源参与约束） */
    private String name;

    /** Skill 描述信息 */
    private String description;

    /** 自由标签数组；持久化为 JSON 字符串列 */
    private String tags;

    /** 来源 {@code SELF} / {@code COMPANY}，以字符串存储 */
    private String source;

    /** 负责人用户 ID */
    @TableField("owner_user_id")
    private String ownerUserId;

    /** 归属工作空间业务编号（前缀 WS-）；存量回填为 WS-DEFAULT */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 生命周期状态 {@code DRAFT} / {@code PUBLISHED} / {@code DEPRECATED}，以字符串存储 */
    private String status;

    /** 当前在线版本号（publish 后写入；rollbackToVersion 后切到目标版本号） */
    @TableField("current_version_num")
    private String currentVersionNum;

    /** 创建人 userId */
    @TableField("create_no")
    private String createNo;

    /** 更新人 userId */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain。
     * <p>装配 transient 依赖（Repository / Gateway / Publisher）由调用方负责。
     */
    public static Skill toDomain(SkillEntity e) {
        if (e == null) {
            return null;
        }
        Skill s = new Skill();
        s.setId(e.getId());
        s.setNum(e.getNum());
        s.setName(e.getName());
        s.setDescription(e.getDescription());
        s.setTags(parseTags(e.getTags()));
        s.setSource(e.getSource() == null ? SkillSource.SELF : SkillSource.valueOf(e.getSource()));
        s.setOwnerUserId(e.getOwnerUserId());
        s.setWorkspaceNum(e.getWorkspaceNum());
        s.setStatus(e.getStatus() == null ? SkillStatus.DRAFT : SkillStatus.valueOf(e.getStatus()));
        s.setCurrentVersionNum(e.getCurrentVersionNum());
        s.setCreateNo(e.getCreateNo());
        s.setUpdateNo(e.getUpdateNo());
        s.setDeleted(e.getDeleted());
        s.setCreateTime(e.getCreateTime());
        s.setUpdateTime(e.getUpdateTime());
        return s;
    }

    /** Domain → Entity。 */
    public static SkillEntity fromDomain(Skill s) {
        SkillEntity e = new SkillEntity();
        e.setId(s.getId());
        e.setNum(s.getNum());
        e.setName(s.getName());
        e.setDescription(s.getDescription());
        e.setTags(s.getTags() == null ? null : JSON.toJSONString(s.getTags()));
        e.setSource(s.getSource() == null ? SkillSource.SELF.name() : s.getSource().name());
        e.setOwnerUserId(s.getOwnerUserId());
        e.setWorkspaceNum(s.getWorkspaceNum());
        e.setStatus(s.getStatus() == null ? SkillStatus.DRAFT.name() : s.getStatus().name());
        e.setCurrentVersionNum(s.getCurrentVersionNum());
        e.setCreateNo(s.getCreateNo());
        e.setUpdateNo(s.getUpdateNo());
        e.setDeleted(s.getDeleted() == null ? 0 : s.getDeleted());
        e.setCreateTime(s.getCreateTime());
        e.setUpdateTime(s.getUpdateTime());
        return e;
    }

    /**
     * 把 JSON 列反序列化为 {@code List<String>}；空值 / 非法 JSON 返回 null。
     */
    private static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }
}
