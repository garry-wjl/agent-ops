package ink.garry.rd.agent.ws.facade.auth.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 本地 JWT Claims。
 * <p>
 * 字段固定为 {@code uuid / account / roles} 三项。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserClaims {

    /**
     * 用户 UUID；当前 rd-agent-be 暂无 User 表，落地为与 account 同值，
     * 后续接入 User 域后再切换为真实主键。
     */
    private String uuid;

    /** 登录账号（AD / 本地用户标识）。 */
    private String account;

    /** 角色列表。 */
    private List<String> roles;
}
