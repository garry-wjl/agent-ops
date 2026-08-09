package ink.garry.rd.agent.ws.client.auth.login.dto;

import lombok.Data;

/**
 * 登录入参 DTO。
 */
@Data
public class LoginParamDTO {

    private String username;
    private String password;
}
