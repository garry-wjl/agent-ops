package ink.garry.rd.agent.ws.infra.common.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 当前请求用户上下文。
 * <p>
 * 由 adapter 层 JwtAuthenticationFilter（SSO 路径）或 UserContextFilter
 * （X-User-Id header 兜底路径）注入。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserContext {
    /** 用户 ID；SSO 路径等同 AD 账号；header 兜底由调用方传入。 */
    private String userId;
    /** 显示名。 */
    private String userName;
    /** 角色列表；SSO 路径由 JWT claims 注入，header 兜底为空列表。 */
    @Builder.Default
    private List<String> roles = Collections.emptyList();
}
