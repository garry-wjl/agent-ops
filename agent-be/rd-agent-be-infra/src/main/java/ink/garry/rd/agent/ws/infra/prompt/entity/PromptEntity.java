package ink.garry.rd.agent.ws.infra.prompt.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.prompt.Prompt;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 资产持久化实体（对应表 {@code prompt}）。
 * <p>
 * 与 domain {@link Prompt} 一一对应：{@code tags} 以 JSON 列落库（fastjson2 序列化
 * List&lt;String&gt;，与 ToolEntity 同模式）；{@code template_content} 为 mediumtext 原文直存
 * （含 {@code {{变量}}}，原样存储不解析）。transient 依赖（Repository / Gateway / Publisher）
 * 由 {@code PromptFactory} 装配，不在此映射。
 */
@Data
@TableName("prompt")
public class PromptEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 PRM，由 {@code PromptGateway.generatePromptNum} 经 BizNumGenerator 生成） */
    private String num;

    /** 归属工作空间业务编号 */
    @TableField("workspace_num")
    private String workspaceNum;

    /** Prompt 引用键；工作空间内唯一 */
    @TableField("prompt_key")
    private String promptKey;

    /** Prompt 描述；≤500 字 */
    private String description;

    /** 模板原文（含 {@code {{变量}}}，原样存储），mediumtext 列 */
    @TableField("template_content")
    private String templateContent;

    /** 标签 JSON 数组，由 fastjson2 序列化 List&lt;String&gt; */
    private String tags;

    /** 负责人 / 创建人用户 ID */
    @TableField("owner_user_id")
    private String ownerUserId;

    /** 创建人工号 */
    @TableField("create_no")
    private String createNo;

    /** 更新人工号（兼任删除人语义） */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（兼任删除时间语义） */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain。
     * <p>JSON 列经 fastjson2 反序列化为标签列表；transient 依赖由调用方（PromptFactory）装配。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域聚合根；e 为 null 返回 null
     */
    public static Prompt toDomain(PromptEntity e) {
        if (e == null) {
            return null;
        }
        Prompt p = new Prompt();
        p.setId(e.getId());
        p.setNum(e.getNum());
        p.setWorkspaceNum(e.getWorkspaceNum());
        p.setPromptKey(e.getPromptKey());
        p.setDescription(e.getDescription());
        p.setTemplateContent(e.getTemplateContent());
        p.setTags(e.getTags() == null ? null : JSON.parseArray(e.getTags(), String.class));
        p.setOwnerUserId(e.getOwnerUserId());
        p.setCreateNo(e.getCreateNo());
        p.setUpdateNo(e.getUpdateNo());
        p.setDeleted(e.getDeleted());
        p.setCreateTime(e.getCreateTime());
        p.setUpdateTime(e.getUpdateTime());
        return p;
    }

    /**
     * Domain → Entity。
     * <p>标签列表经 fastjson2 序列化为 JSON 列；deleted 为 null 时兜底 0（NOT NULL 列约束）。
     *
     * @param p 领域聚合根
     * @return MyBatis 持久化实体
     */
    public static PromptEntity fromDomain(Prompt p) {
        PromptEntity e = new PromptEntity();
        e.setId(p.getId());
        e.setNum(p.getNum());
        e.setWorkspaceNum(p.getWorkspaceNum());
        e.setPromptKey(p.getPromptKey());
        e.setDescription(p.getDescription());
        e.setTemplateContent(p.getTemplateContent());
        e.setTags(p.getTags() == null ? null : JSON.toJSONString(p.getTags()));
        e.setOwnerUserId(p.getOwnerUserId());
        e.setCreateNo(p.getCreateNo());
        e.setUpdateNo(p.getUpdateNo());
        e.setDeleted(p.getDeleted() == null ? 0 : p.getDeleted());
        e.setCreateTime(p.getCreateTime());
        e.setUpdateTime(p.getUpdateTime());
        return e;
    }
}
