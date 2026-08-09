package ink.garry.rd.agent.ws.adapter.auth;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.auth.command.AuthLoginCommandService;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginParamDTO;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginResultDTO;
import ink.garry.rd.agent.ws.client.auth.login.param.LoginParam;
import ink.garry.rd.agent.ws.client.auth.login.vo.LoginResultVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.infra.auth.token.JwtProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 用户名密码登录 / 登出。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthLoginController extends BaseController {

    @Resource
    private AuthLoginCommandService authLoginCommandService;
    @Resource
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginParam param, HttpServletResponse response) {
        LoginParamDTO dto = new LoginParamDTO();
        dto.setUsername(param.getUsername());
        dto.setPassword(param.getPassword());
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
}
