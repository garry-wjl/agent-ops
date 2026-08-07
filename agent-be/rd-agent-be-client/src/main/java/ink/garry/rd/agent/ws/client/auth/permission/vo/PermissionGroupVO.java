package ink.garry.rd.agent.ws.client.auth.permission.vo;

import lombok.Data;

import java.util.List;

/**
 * 权限分组 VO（按资源域聚合的权限项列表）。
 * 用于 {@code GET /api/v1/permissions/list} 与 {@code RoleDetailVO} 的权限展开。
 */
@Data
public class PermissionGroupVO {

    /** 资源域代码 */
    private String resourceDomain;

    /** 资源域中文名 */
    private String resourceDomainName;

    /** 该域下全部权限项 */
    private List<PermissionVO> permissions;
}
