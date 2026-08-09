package ink.garry.rd.agent.ws.client.auth.login.vo;

import lombok.Data;

/**
 * 登录成功响应 VO（不含 token）。
 */
@Data
public class LoginResultVO {

    private String userNum;
    private String username;
}
