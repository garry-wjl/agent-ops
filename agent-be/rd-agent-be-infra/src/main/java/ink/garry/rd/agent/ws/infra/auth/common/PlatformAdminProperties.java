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

    /** 平台管理员工号清单（启动时被绑定为 platform_admin） */
    private List<String> platformAdmins = new ArrayList<>();
}
