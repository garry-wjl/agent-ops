package ink.garry.rd.agent.ws.client.auth.roleassignment.param;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户-角色批量绑定入参（整空间覆盖）。
 * 由 {@code WorkspaceUpdateParam.memberRoles} 内嵌触发；亦可独立暴露给批量场景接口。
 */
@Data
public class UserRoleBindParam {

    /** 工作空间业务编号（必填） */
    private String workspaceNum;

    /**
     * 用户-角色映射（empNo → roleNum 列表）。
     * application 层会对照当前空间已有绑定做覆盖式更新。
     */
    private Map<String, List<String>> userRoles;
}
