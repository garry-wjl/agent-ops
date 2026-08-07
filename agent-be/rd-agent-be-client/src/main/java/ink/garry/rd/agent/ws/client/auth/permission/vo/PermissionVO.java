package ink.garry.rd.agent.ws.client.auth.permission.vo;

import lombok.Data;

/**
 * 单个权限项 VO（在勾选面板里的最小单元）。
 */
@Data
public class PermissionVO {

    /** 权限编码（resource:action） */
    private String code;

    /** 权限中文名 */
    private String name;

    /** 权限描述 */
    private String description;

    /**
     * 当前角色是否已勾选此权限（仅在 {@code RoleDetailVO} 上下文里有意义；
     * 在 {@code /permissions/list} 全集查询时统一返回 null）。
     */
    private Boolean selected;
}
