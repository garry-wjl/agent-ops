package ink.garry.rd.agent.ws.client.auth.role.param;

import lombok.Data;

import java.util.List;

/**
 * 编辑空间自定义角色入参。
 * 整体覆盖：name + description + permissionCodes 三者必须全部同步更新；
 * 由 {@code RoleCommandController.updateRole} 接收。
 */
@Data
public class RoleUpdateParam {

    /** 角色业务编号（必填，RL-SPACE-* 才允许编辑） */
    private String roleNum;

    /** 新角色名（必填） */
    private String name;

    /** 新角色描述（可空） */
    private String description;

    /** 新权限码集合（必填，覆盖式替换原集合） */
    private List<String> permissionCodes;
}
