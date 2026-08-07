package ink.garry.rd.agent.ws.infra.auth.userrolebinding.gateway;

import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.gateway.UserRoleBindingGateway;
import org.springframework.stereotype.Component;

/**
 * UserRoleBinding 网关实现：生成业务编码 num。
 * <ul>
 *   <li>PLATFORM → UR-PLATFORM-{userId}</li>
 *   <li>SPACE    → UR-SPACE-{workspaceNum}-{userId}</li>
 * </ul>
 */
@Component
public class UserRoleBindingGatewayImpl implements UserRoleBindingGateway {

    @Override
    public String generateBindingNum(RoleBindingType roleType, String workspaceNum, String userId) {
        if (roleType == RoleBindingType.PLATFORM) {
            return "UR-PLATFORM-" + userId;
        }
        String ws = workspaceNum == null ? AuthzDomainConstants.PLATFORM_WORKSPACE_NUM : workspaceNum;
        return "UR-SPACE-" + ws + "-" + userId;
    }
}
