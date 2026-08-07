package ink.garry.rd.agent.ws.client.auth.login.dto;

import lombok.Data;

/**
 * 登录结果 DTO（含 token，由 adapter 写 Cookie 后剥离）。
 */
@Data
public class LoginResultDTO {

    private String userNum;
    private String username;
    /** JWT 明文；仅 adapter 用于 Set-Cookie，不回传前端 body。 */
    private String token;
}
