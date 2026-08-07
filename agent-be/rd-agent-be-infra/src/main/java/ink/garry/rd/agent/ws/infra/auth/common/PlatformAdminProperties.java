package ink.garry.rd.agent.ws.infra.auth.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台管理员配置（{@code app.auth.platform-admins}）。
 * <p>启动时由 {@link PlatformAdminBootstrapper} 把清单中的工号 upsert 到 {@code user_workspace_role}
 * (workspace_num=SYSTEM, role_num=RL-PLATFORM-ADMIN)，使其成为运行时 platform_admin。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class PlatformAdminProperties {

    /** 平台管理员用户名清单（启动时确保 sys_user 存在并绑定 platform_admin） */
    private List<String> platformAdmins = new ArrayList<>();

    /**
     * 引导创建用户时的默认初始密码（仅 bootstrap；生产务必覆盖）。
     */
    private String bootstrapDefaultPassword = "ChangeMe@123456";
}
