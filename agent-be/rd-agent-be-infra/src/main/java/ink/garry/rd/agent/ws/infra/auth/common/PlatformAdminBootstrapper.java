package ink.garry.rd.agent.ws.infra.auth.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 旧平台管理员引导器（已弃用）。
 * <p>
 * 逻辑已迁移至 {@code ink.garry.rd.agent.ws.infra.user.bootstrap.UserPlatformAdminBootstrapper}，
 * 本类保留为空壳以免误配，不再执行绑定。
 */
@Slf4j
@Component
@Order(99)
public class PlatformAdminBootstrapper implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        log.debug("[PlatformAdminBootstrapper] delegated to UserPlatformAdminBootstrapper");
    }
}
