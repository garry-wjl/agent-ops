package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.command.AuthLoginCommandService;
import ink.garry.rd.agent.ws.application.auth.command.SliderCaptchaService;
import ink.garry.rd.agent.ws.client.auth.captcha.vo.SliderCaptchaVO;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginParamDTO;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginResultDTO;
import ink.garry.rd.agent.ws.client.auth.login.param.LoginParam;
import ink.garry.rd.agent.ws.client.auth.login.vo.LoginResultVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.infra.auth.token.JwtProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 用户名密码登录 / 登出 / 滑块验证码。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthLoginController extends BaseController {

    @Resource
    private AuthLoginCommandService authLoginCommandService;
    @Resource
    private SliderCaptchaService sliderCaptchaService;
    @Resource
    private JwtProperties jwtProperties;

    /**
     * 拉取滑块验证码挑战（公开接口）。
     *
     * @return 背景图 + 拼图 + captchaId
     */
    @GetMapping("/captcha/slider")
    public Result<SliderCaptchaVO> sliderCaptcha() {
        return ok(sliderCaptchaService.createChallenge());
    }

    /**
     * 登录。
     *
     * @param param    用户名密码及可选滑块
     * @param request  用于解析客户端 IP
     * @param response 写 Cookie
     * @return 用户摘要
     */
    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginParam param,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        LoginParamDTO dto = new LoginParamDTO();
        dto.setUsername(param.getUsername());
        dto.setPassword(param.getPassword());
        dto.setCaptchaId(param.getCaptchaId());
        dto.setSlideX(param.getSlideX());
        dto.setClientIp(resolveClientIp(request));
        LoginResultDTO result = authLoginCommandService.login(dto);

        Duration maxAge = Duration.ofHours(Math.max(1L, jwtProperties.getExpirationHours()));
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), result.getToken())
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .maxAge(maxAge)
                .sameSite(jwtProperties.getCookieSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        LoginResultVO vo = new LoginResultVO();
        vo.setUserNum(result.getUserNum());
        vo.setUsername(result.getUsername());
        return ok(vo);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(jwtProperties.getCookieSameSite())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ok(null);
    }

    /**
     * 解析客户端 IP：优先 X-Forwarded-For 首段，否则 remoteAddr。
     *
     * @param request HTTP 请求
     * @return IP 字符串
     */
    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
