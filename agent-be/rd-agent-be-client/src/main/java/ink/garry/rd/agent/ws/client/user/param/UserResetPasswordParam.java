package ink.garry.rd.agent.ws.client.user.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置密码 HTTP 入参。
 */
@Data
public class UserResetPasswordParam {

    @NotBlank(message = "用户编号不能为空")
    private String num;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须在 8~64 之间")
    private String password;
}
