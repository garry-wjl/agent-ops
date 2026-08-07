package ink.garry.rd.agent.ws.client.auth.role.dto;

import lombok.Data;

import java.util.Date;
import java.util.Set;

/**
 * 角色跨层 DTO（infra ↔ application）。
 * 包含全部领域字段；不直接暴露给 adapter（adapter 用 {@code RoleVO} / {@code RoleDetailVO}）。
 */
@Data
public class RoleDTO {

    /** 自增主键 */
    private Long id;

    /** 业务编号 RL-PLATFORM-* / RL-SPACE-* */
    private String num;

    /** 角色名 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 作用域：PLATFORM / SPACE */
    private String scope;

    /** scope=SPACE 时所属空间编号；scope=PLATFORM 时为 null */
    private String workspaceNum;

    /** 是否内置（true 时禁止编辑/删除） */
    private Boolean builtin;

    /** 权限码集合 */
    private Set<String> permissionCodes;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 审计字段 - 创建人工号 */
    private String createNo;

    /** 审计字段 - 更新人工号 */
    private String updateNo;

    /** 审计字段 - 创建时间（毫秒） */
    private Date createTime;

    /** 审计字段 - 更新时间（毫秒） */
    private Date updateTime;
}
