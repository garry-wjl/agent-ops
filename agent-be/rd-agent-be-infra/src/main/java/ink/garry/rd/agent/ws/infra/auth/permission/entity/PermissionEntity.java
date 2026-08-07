package ink.garry.rd.agent.ws.infra.auth.permission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.auth.permission.PermissionMetadata;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限元数据持久化实体（对应表 {@code permission}）。
 * <p>scope 列（V31 新增）驱动平台域/空间域的过滤，替代 AuthzQueryService 中的硬编码集合。</p>
 */
@Data
@TableName("permission")
public class PermissionEntity {

    /** 主键：权限编码 resource:action */
    @TableId
    private String code;

    /** 权限中文名 */
    private String name;

    @TableField("resource_domain")
    private String resourceDomain;

    /**
     * 适用角色范围（V31 新增）。
     * <ul>
     *   <li>{@code PLATFORM}：仅平台角色可分配（workspace / system / role_manage / user_role 域）</li>
     *   <li>{@code SPACE}：空间角色可分配（agent / skill / tool / ... 域）</li>
     * </ul>
     */
    private String scope;

    /** 权限描述 */
    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** Entity → Domain 值对象。 */
    public PermissionMetadata toDomain() {
        return new PermissionMetadata(
                this.code,
                this.name,
                this.resourceDomain,
                this.scope,
                this.description,
                this.sortOrder == null ? 0 : this.sortOrder);
    }
}
