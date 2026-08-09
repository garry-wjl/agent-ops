package ink.garry.rd.agent.ws.infra.user.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.factory.UserFactory;
import ink.garry.rd.agent.ws.domain.user.gateway.UserGateway;
import ink.garry.rd.agent.ws.domain.user.repository.UserRepository;
import ink.garry.rd.agent.ws.domain.user.valueobject.UserStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link UserFactory} 实现：装配 Repository / Gateway / EventPublisher。
 */
@Component
public class UserFactoryImpl implements UserFactory {

    @Resource
    private UserRepository userRepository;
    @Resource
    private UserGateway userGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public User create(String username, String email, String remark, String rawPassword) {
        Assert.notBlank(username, "用户名不能为空");
        Assert.notBlank(email, "邮箱不能为空");
        Assert.notBlank(rawPassword, "初始密码不能为空");
        String hash = userGateway.hashPassword(rawPassword);
        User user = new User(username, email, remark, hash,
                userRepository, userGateway, domainEventPublisher);
        user.setStatus(UserStatus.ENABLED);
        return user;
    }

    @Override
    public User createByNum(String num) {
        Assert.notBlank(num, "用户业务编号不能为空");
        return wire(userRepository.findByNum(num));
    }

    private User wire(User user) {
        if (user == null) {
            return null;
        }
        user.setUserRepository(userRepository);
        user.setUserGateway(userGateway);
        user.setDomainEventPublisher(domainEventPublisher);
        return user;
    }
}
