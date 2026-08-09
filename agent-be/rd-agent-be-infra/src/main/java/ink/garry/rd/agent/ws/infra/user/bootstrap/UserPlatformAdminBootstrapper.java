package ink.garry.rd.agent.ws.infra.user.bootstrap;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.factory.UserRoleBindingFactory;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.factory.UserFactory;
import ink.garry.rd.agent.ws.infra.auth.common.PlatformAdminProperties;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import ink.garry.rd.agent.ws.infra.user.entity.UserEntity;
import ink.garry.rd.agent.ws.infra.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 平台管理员引导：确保配置中的 username 在 {@code sys_user} 存在，并以 {@code User.num}
 * 绑定 {@code RL-PLATFORM-ADMIN}。
 * <p>
 * 替代仅写绑定、不建用户的旧 {@code PlatformAdminBootstrapper}。
 */
@Slf4j
@Component
@Order(100)
public class UserPlatformAdminBootstrapper implements ApplicationRunner {

    @Resource
    private PlatformAdminProperties platformAdminProperties;
    @Resource
    private UserFactory userFactory;
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserRoleBindingFactory userRoleBindingFactory;
    @Resource
    private UserRoleBindingMapper userRoleBindingMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (platformAdminProperties.getPlatformAdmins() == null
                || platformAdminProperties.getPlatformAdmins().isEmpty()) {
            log.info("[UserPlatformAdminBootstrapper] platform-admins empty, skip");
            return;
        }
        String defaultPassword = platformAdminProperties.getBootstrapDefaultPassword();
        if (StrUtil.isBlank(defaultPassword)) {
            defaultPassword = "ChangeMe@123456";
        }
        for (String username : platformAdminProperties.getPlatformAdmins()) {
            if (StrUtil.isBlank(username)) {
                continue;
            }
            try {
                ensureUserAndAdmin(username.trim(), defaultPassword);
            } catch (Exception ex) {
                log.error("[UserPlatformAdminBootstrapper] failed for username={}", username, ex);
            }
        }
    }

    private void ensureUserAndAdmin(String username, String defaultPassword) {
        UserEntity existing = userMapper.findByUsername(username);
        String userNum;
        if (existing == null) {
            String email = username.contains("@") ? username : username + "@local.dev";
            User user = userFactory.create(username, email, "bootstrap platform admin", defaultPassword);
            user.save("SYSTEM");
            userNum = user.getNum();
            log.info("[UserPlatformAdminBootstrapper] created sys_user username={} num={}", username, userNum);
        } else {
            userNum = existing.getNum();
        }

        // 若旧绑定仍以 username 为 user_id，迁移到 num
        migrateLegacyBinding(username, userNum);

        if (userRoleBindingMapper.countPlatformAdminRows(userNum) > 0) {
            return;
        }
        UserRoleBinding binding = userRoleBindingFactory.buildPlatformBindingByUser(userNum);
        Set<String> roles = binding == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(binding.getRoleNums());
        roles.add(AuthzDomainConstants.ROLE_PLATFORM_ADMIN);
        if (binding == null) {
            binding = userRoleBindingFactory.buildBinding(
                    RoleBindingType.PLATFORM, userNum,
                    AuthzDomainConstants.PLATFORM_WORKSPACE_NUM, roles);
        } else {
            binding.setRoleNums(roles);
        }
        binding.save("SYSTEM");
        log.info("[UserPlatformAdminBootstrapper] bound platform-admin userNum={} username={}", userNum, username);
    }

    private void migrateLegacyBinding(String username, String userNum) {
        if (username.equals(userNum)) {
            return;
        }
        UserRoleBinding legacy = userRoleBindingFactory.buildPlatformBindingByUser(username);
        if (legacy == null) {
            return;
        }
        UserRoleBinding byNum = userRoleBindingFactory.buildPlatformBindingByUser(userNum);
        if (byNum == null) {
            Set<String> roles = new LinkedHashSet<>(legacy.getRoleNums());
            UserRoleBinding migrated = userRoleBindingFactory.buildBinding(
                    RoleBindingType.PLATFORM, userNum,
                    AuthzDomainConstants.PLATFORM_WORKSPACE_NUM, roles);
            migrated.save("SYSTEM");
        }
        legacy.delete("SYSTEM");
        log.info("[UserPlatformAdminBootstrapper] migrated platform binding {} -> {}", username, userNum);
    }
}
