package ink.garry.rd.agent.ws.infra.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 资源文件树持久化实体（对应表 {@code skill_resource_file}，v3.0 新增）。
 * <p>
 * 一文件 / 文件夹一行，以 {@code (owner_type, owner_num)} 二元组归属：
 * <ul>
 *   <li>{@code owner_type=SKILL}，{@code owner_num=skill.num}：Skill 草稿态可编辑文件树；</li>
 *   <li>{@code owner_type=VERSION}，{@code owner_num=skill_version.num}：发布版本的不可变快照树。</li>
 * </ul>
 * 文件内容直接入库（替代对象存储）：文本 UTF-8 原文（{@code encoding=text}），
 * 图片等二进制 Base64 串（{@code encoding=base64}）；文件夹的 encoding / mime / content 为空。
 */
@Data
@TableName("skill_resource_file")
public class SkillResourceFileEntity {

    /** owner_type 取值：Skill 草稿态可编辑文件树。 */
    public static final String OWNER_TYPE_SKILL = "SKILL";

    /** owner_type 取值：发布版本的不可变快照树。 */
    public static final String OWNER_TYPE_VERSION = "VERSION";

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属类型：{@code SKILL}（草稿树） / {@code VERSION}（版本快照树） */
    @TableField("owner_type")
    private String ownerType;

    /** 归属业务编号：skill.num 或 skill_version.num */
    @TableField("owner_num")
    private String ownerNum;

    /** 资源相对路径（树内唯一标识，如 {@code references/guide.md}） */
    private String path;

    /** 资源类型：{@code FILE} / {@code FOLDER} */
    private String type;

    /** 父节点相对路径；根节点为 null */
    @TableField("parent_path")
    private String parentPath;

    /** 内容编码方式：{@code text} / {@code base64}；文件夹为空 */
    private String encoding;

    /** MIME 类型（如 {@code text/markdown}、{@code image/png}）；文件夹为空 */
    private String mime;

    /** 文件内容：文本原文或 Base64 串；文件夹为空（列类型 LONGTEXT） */
    private String content;

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
     * 资源节点值对象 → Entity（v3.0）。
     * <p>审计字段（createNo/updateNo/createTime/updateTime/deleted）由调用方在 RepositoryImpl
     * 级联保存时统一填充（与父聚合审计一致）。
     *
     * @param ownerType 归属类型（SKILL / VERSION）
     * @param ownerNum  归属业务编号
     * @param vo        资源节点值对象
     * @return 持久化实体（不含审计字段）
     */
    public static SkillResourceFileEntity fromValueObject(String ownerType, String ownerNum, SkillResourceFile vo) {
        SkillResourceFileEntity e = new SkillResourceFileEntity();
        e.setOwnerType(ownerType);
        e.setOwnerNum(ownerNum);
        e.setPath(vo.getPath());
        e.setType(vo.getType() == null ? null : vo.getType().name());
        e.setParentPath(vo.getParentPath());
        e.setEncoding(vo.getEncoding());
        e.setMime(vo.getMime());
        e.setContent(vo.getContent());
        return e;
    }

    /**
     * Entity → 资源节点值对象（v3.0）。
     *
     * @param e 持久化实体
     * @return 资源节点值对象
     */
    public static SkillResourceFile toValueObject(SkillResourceFileEntity e) {
        if (e == null) {
            return null;
        }
        return SkillResourceFile.builder()
                .path(e.getPath())
                .type(e.getType() == null ? null : SkillResourceFileType.valueOf(e.getType()))
                .name(deriveName(e.getPath()))
                .parentPath(e.getParentPath())
                .encoding(e.getEncoding())
                .mime(e.getMime())
                .content(e.getContent())
                .build();
    }

    /** 从相对路径推导节点名（最后一段）。 */
    private static String deriveName(String path) {
        if (path == null) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
