package ink.garry.rd.agent.ws.infra.auth.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路由-权限映射持久化实体（对应表 {@code route_permission}）。
 * <p>替代 {@code RouteRoleMapping} 中的硬编码路径映射；DB 为唯一真相源。</p>
 */
@Data
@TableName("route_permission")
public class RoutePermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Ant 风格路径模式，如 {@code /api/v1/agents/create} 或
     * {@code /api/v1/agents/apiKey/command/**}。
     */
    @TableField("path_pattern")
    private String pathPattern;

    /**
     * 任一命中即放行的权限码 JSON 数组。
     * 空数组表示仅登录即可访问。
     * MyBatis-Plus 以 String 保存，业务层负责 JSON 解析。
     */
    @TableField("permission_codes")
    private String permissionCodes;

    /** 路由业务含义备注 */
    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
