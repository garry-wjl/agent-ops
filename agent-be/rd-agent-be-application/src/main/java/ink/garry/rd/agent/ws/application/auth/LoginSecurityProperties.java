package ink.garry.rd.agent.ws.application.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全：失败锁定阈值与滑块验证码参数（{@code app.auth.login-security.*}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth.login-security")
public class LoginSecurityProperties {

    /** 触发滑块校验的连续失败次数阈值（含本次），默认 3 */
    private int failThreshold = 3;

    /** 失败计数 TTL（分钟） */
    private int failTtlMinutes = 15;

    /** 滑块会话 TTL（秒） */
    private int captchaTtlSeconds = 300;

    /** 滑块 X 轴允许误差（像素） */
    private int captchaTolerancePx = 6;
}
