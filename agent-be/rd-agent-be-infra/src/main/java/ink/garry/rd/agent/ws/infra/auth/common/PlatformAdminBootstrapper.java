package ink.garry.rd.agent.ws.infra.auth.common;

import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.factory.UserRoleBindingFactory;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 平台管理员引导器。
 * <p>启动时把 {@link PlatformAdminProperties#getPlatformAdmins()} 中的工号绑定为 platform_admin。
 * 已是 platform_admin 的工号跳过；否则把 RL-PLATFORM-ADMIN 追加到其平台角色集合并落库。</p>
 */
@Slf4j
@Component
@Order(100)
public class PlatformAdminBootstrapper implements ApplicationRunner {

    @Resource
    private PlatformAdminProperties platformAdminProperties;

    @Resource
    private UserRoleBindingFactory userRoleBindingFactory;

    @Resource
    private UserRoleBindingMapper userRoleBindingMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (platformAdminProperties.getPlatformAdmins() == null
                || platformAdminProperties.getPlatformAdmins().isEmpty()) {
            log.info("[PlatformAdminBootstrapper] app.auth.platform-admins is empty, skip");
            return;
        }
        for (String empNo : platformAdminProperties.getPlatformAdmins()) {
            if (empNo == null || empNo.isBlank()) {
                continue;
            }
            if (userRoleBindingMapper.countPlatformAdminRows(empNo) > 0) {
                continue;
            }
            UserRoleBinding existing = userRoleBindingFactory.buildPlatformBindingByUser(empNo);
            Set<String> roles = existing == null
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(existing.getRoleNums());
            roles.add(AuthzDomainConstants.ROLE_PLATFORM_ADMIN);
            UserRoleBinding binding = existing == null
                    ? userRoleBindingFactory.buildBinding(
                            RoleBindingType.PLATFORM, empNo,
                            AuthzDomainConstants.PLATFORM_WORKSPACE_NUM, roles)
                    : existing;
            if (existing != null) {
                binding.setRoleNums(roles);
            }
            binding.save("SYSTEM");
            log.info("[PlatformAdminBootstrapper] bound platform-admin empNo={}", empNo);
        }
    }
}
