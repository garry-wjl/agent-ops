package ink.garry.rd.agent.ws.client.auth.captcha.vo;

import lombok.Data;

/**
 * 滑块验证码挑战（前端展示用）。
 */
@Data
public class SliderCaptchaVO {

    /** 挑战 ID，登录时回传 */
    private String captchaId;

    /** 背景图 data URL（含缺口） */
    private String backgroundImage;

    /** 滑块拼图 data URL */
    private String sliderImage;

    /** 拼图在背景上的 Y 偏移（像素） */
    private int sliderY;
}
