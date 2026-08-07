package ink.garry.rd.agent.ws.domain.auth.userrolebinding.gateway;

import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;

/**
 * UserRoleBinding 领域网关。
 */
public interface UserRoleBindingGateway {

    /**
     * 生成业务编码 num。
     * <ul>
     *   <li>PLATFORM：{@code UR-PLATFORM-{userId}}</li>
     *   <li>SPACE：{@code UR-SPACE-{workspaceNum}-{userId}}</li>
     * </ul>
     */
    String generateBindingNum(RoleBindingType roleType, String workspaceNum, String userId);
}
