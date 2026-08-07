package ink.garry.rd.agent.ws.infra.auth.role.gateway;

import cn.hutool.core.lang.UUID;
import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.gateway.RoleGateway;
import ink.garry.rd.agent.ws.infra.auth.role.mapper.RoleMapper;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Role 网关实现：编号生成 / 唯一性预检 / 绑定数统计。
 */
@Component
public class RoleGatewayImpl implements RoleGateway {

    private static final int SUFFIX_LENGTH = 12;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private UserRoleBindingMapper roleAssignmentMapper;

    @Override
    public String generateRoleNum(RoleScope scope, String workspaceNum) {
        String prefix = scope == RoleScope.PLATFORM
                ? AuthzDomainConstants.ROLE_NUM_PREFIX_PLATFORM
                : AuthzDomainConstants.ROLE_NUM_PREFIX_SPACE;
        String suffix = UUID.randomUUID().toString(true).substring(0, SUFFIX_LENGTH).toUpperCase();
        return prefix + suffix;
    }

    @Override
    public boolean isNameDuplicate(RoleScope scope, String workspaceNum, String name, String excludeRoleNum) {
        Long hit = roleMapper.findIdByName(name, scope.name(), workspaceNum, excludeRoleNum);
        return hit != null;
    }

    @Override
    public long countAssignedUsers(String roleNum) {
        return roleAssignmentMapper.countByRoleNum(roleNum);
    }
}
