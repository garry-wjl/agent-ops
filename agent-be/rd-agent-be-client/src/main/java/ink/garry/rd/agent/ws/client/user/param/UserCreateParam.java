package ink.garry.rd.agent.ws.client.user.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户 HTTP 入参。
 */
@Data
public class UserCreateParam {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须在 8~64 之间")
    private String password;
}
