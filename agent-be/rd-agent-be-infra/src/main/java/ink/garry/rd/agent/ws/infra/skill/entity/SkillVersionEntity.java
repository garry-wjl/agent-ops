package ink.garry.rd.agent.ws.infra.skill.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.skill.SkillVersion;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SkillVersion 持久化实体（对应表 {@code skill_version}）。
 * <p>
 * v3.0 字段集（与 domain {@link SkillVersion} 一一对应）：
 * {@code num / skill_num / version / name / description / tags / status} + 审计字段。
 * <p>
 * <b>v3.0 变更</b>：删除 {@code skill_file_key}（OSS key）—— 版本资源文件树快照改由
 * {@code skill_resource_file} 表（owner_type=VERSION）承载，由 {@code SkillVersionRepositoryImpl}
 * 级联读写，不在本实体映射。
 * <p>
 * v2.5~v2.8 已删除字段：{@code semver_major} / {@code semver_minor} / {@code semver_patch} /
 * {@code change_level} / {@code change_note} / {@code skill_file_hash} / {@code published_by} /
 * {@code published_at} / {@code current_flag} / {@code snapshot}。
 */
@Data
@TableName("skill_version")
public class SkillVersionEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 SVN，由 {@code SkillVersionGateway.generateSkillVersionNum} 生成） */
    private String num;

    /** 所属 Skill 业务编号 */
    @TableField("skill_num")
    private String skillNum;

    /** 版本号字符串（约定 {@code vX.Y.Z}；同 skillNum 下唯一） */
    private String version;

    /** Skill 发布时的名称快照 */
    private String name;

    /** Skill 发布时的描述快照 */
    private String description;

    /** Skill 发布时的标签数组快照；持久化为 JSON 字符串列 */
    private String tags;

    /** 归属工作空间业务编号（前缀 WS-）；存量回填为 WS-DEFAULT */
    @TableField("workspace_num")
    private String workspaceNum;

    /**
     * 版本生命周期状态（v2.8 新增）：{@code DRAFT} / {@code PUBLISHED} / {@code DEPRECATED}，以字符串存储。
     */
    private String status;

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

    /** Entity → Domain；transient 依赖由调用方装配。 */
    public static SkillVersion toDomain(SkillVersionEntity e) {
        if (e == null) {
            return null;
        }
        SkillVersion v = new SkillVersion();
        v.setId(e.getId());
        v.setNum(e.getNum());
        v.setSkillNum(e.getSkillNum());
        v.setVersion(e.getVersion());
        v.setName(e.getName());
        v.setDescription(e.getDescription());
        v.setTags(parseTags(e.getTags()));
        v.setWorkspaceNum(e.getWorkspaceNum());
        v.setStatus(e.getStatus() == null ? SkillStatus.DRAFT : SkillStatus.valueOf(e.getStatus()));
        v.setCreateNo(e.getCreateNo());
        v.setUpdateNo(e.getUpdateNo());
        v.setDeleted(e.getDeleted());
        v.setCreateTime(e.getCreateTime());
        v.setUpdateTime(e.getUpdateTime());
        return v;
    }

    /** Domain → Entity。 */
    public static SkillVersionEntity fromDomain(SkillVersion v) {
        SkillVersionEntity e = new SkillVersionEntity();
        e.setId(v.getId());
        e.setNum(v.getNum());
        e.setSkillNum(v.getSkillNum());
        e.setVersion(v.getVersion());
        e.setName(v.getName());
        e.setDescription(v.getDescription());
        e.setTags(v.getTags() == null ? null : JSON.toJSONString(v.getTags()));
        e.setWorkspaceNum(v.getWorkspaceNum());
        e.setStatus(v.getStatus() == null ? SkillStatus.DRAFT.name() : v.getStatus().name());
        e.setCreateNo(v.getCreateNo());
        e.setUpdateNo(v.getUpdateNo());
        e.setDeleted(v.getDeleted() == null ? 0 : v.getDeleted());
        e.setCreateTime(v.getCreateTime());
        e.setUpdateTime(v.getUpdateTime());
        return e;
    }

    /** 把 JSON 列反序列化为 {@code List<String>}；空值 / 非法 JSON 返回 null。 */
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
