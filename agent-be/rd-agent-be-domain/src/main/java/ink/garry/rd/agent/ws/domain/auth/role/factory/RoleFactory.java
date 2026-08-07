package ink.garry.rd.agent.ws.domain.auth.role.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.Role;
import ink.garry.rd.agent.ws.domain.auth.role.gateway.RoleGateway;
import ink.garry.rd.agent.ws.domain.auth.role.repository.RoleRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Role 领域工厂。
 * <p>提供两个 build 方法：</p>
 * <ul>
 *   <li>{@link #buildRole}：以用户可填字段构造新聚合（builtin=false 强制）；num 在 save 中由 Gateway 生成。</li>
 *   <li>{@link #buildRoleByNum}：按业务编号加载并装配依赖。</li>
 * </ul>
 */
@Component
public class RoleFactory {

    @Resource
    private RoleRepository roleRepository;
    @Resource
    private RoleGateway roleGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 构造一条新的自定义 Role 聚合（未落库）。
     *
     * @param name            角色名
     * @param description     角色描述（可空）
     * @param scope           作用域
     * @param workspaceNum    归属空间编号（scope=PLATFORM 时传 null）
     * @param permissionCodes 权限码集合
     * @return 已装配依赖、可直接 save 的 Role 聚合
     */
    public Role buildRole(String name,
                          String description,
                          RoleScope scope,
                          String workspaceNum,
                          Collection<String> permissionCodes) {
        Assert.notBlank(name, "角色名不能为空");
        Assert.notNull(scope, "角色作用域不能为空");
        Set<String> codes = permissionCodes == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(permissionCodes);
        return new Role(name, description, scope, workspaceNum, codes,
                roleRepository, roleGateway, domainEventPublisher);
    }

    /**
     * 按业务编号加载 Role 并装配依赖。
     *
     * @param num 角色业务编号
     * @return 已装配依赖的 Role 聚合；不存在时返回 null
     */
    public Role buildRoleByNum(String num) {
        Assert.notBlank(num, "角色业务编号不能为空");
        Role role = roleRepository.findByNum(num);
        if (role == null) {
            return null;
        }
        wireRole(role);
        return role;
    }

    /** 把 3 个依赖一次性注入 Role 聚合根。 */
    private void wireRole(Role role) {
        role.setRoleRepository(this.roleRepository);
        role.setRoleGateway(this.roleGateway);
        role.setDomainEventPublisher(this.domainEventPublisher);
    }
}
