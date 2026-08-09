package ink.garry.rd.agent.ws.client.auth.login.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名密码登录 HTTP 入参。
 */
@Data
public class LoginParam {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
