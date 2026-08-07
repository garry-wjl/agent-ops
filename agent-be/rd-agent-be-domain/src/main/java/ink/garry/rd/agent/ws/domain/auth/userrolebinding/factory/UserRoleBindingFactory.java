package ink.garry.rd.agent.ws.domain.auth.userrolebinding.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.gateway.UserRoleBindingGateway;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * UserRoleBinding 领域工厂。
 */
@Component
public class UserRoleBindingFactory {

    @Resource
    private UserRoleBindingRepository userRoleBindingRepository;
    @Resource
    private UserRoleBindingGateway userRoleBindingGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /** 构造一条新的 UserRoleBinding 聚合（未落库）。 */
    public UserRoleBinding buildBinding(RoleBindingType roleType,
                                        String userId,
                                        String workspaceNum,
                                        Collection<String> roleNums) {
        Assert.notNull(roleType, "roleType 不能为空");
        Assert.notBlank(userId, "userId 不能为空");
        return new UserRoleBinding(roleType, userId, workspaceNum,
                roleNums == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roleNums),
                userRoleBindingRepository, userRoleBindingGateway, domainEventPublisher);
    }

    /** 按 (userId, workspaceNum) 加载聚合；不存在时返回 null。 */
    public UserRoleBinding buildBindingByUser(String userId, String workspaceNum) {
        Assert.notBlank(userId, "userId 不能为空");
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        UserRoleBinding binding = userRoleBindingRepository.findByUserAndWorkspace(userId, workspaceNum);
        if (binding == null) {
            return null;
        }
        wire(binding);
        return binding;
    }

    /** 便捷：平台绑定（workspaceNum 强制 SYSTEM）。 */
    public UserRoleBinding buildPlatformBindingByUser(String userId) {
        return buildBindingByUser(userId, AuthzDomainConstants.PLATFORM_WORKSPACE_NUM);
    }

    private void wire(UserRoleBinding binding) {
        binding.setUserRoleBindingRepository(this.userRoleBindingRepository);
        binding.setUserRoleBindingGateway(this.userRoleBindingGateway);
        binding.setDomainEventPublisher(this.domainEventPublisher);
    }
}
