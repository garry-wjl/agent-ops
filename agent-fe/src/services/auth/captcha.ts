/**
 * 登录滑块验证码 API
 */
import { get } from '../request';

export interface SliderCaptchaVO {
  captchaId: string;
  backgroundImage: string;
  sliderImage: string;
  sliderY: number;
}

export const authCaptchaApi = {
  fetchSlider: () => get<SliderCaptchaVO>('/api/v1/auth/captcha/slider'),
};
