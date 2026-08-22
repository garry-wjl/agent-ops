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

    /** 滑块挑战 ID；失败达阈值后必填 */
    private String captchaId;

    /** 滑块拖动后的 X 偏移（像素）；失败达阈值后必填 */
    private Integer slideX;
}
