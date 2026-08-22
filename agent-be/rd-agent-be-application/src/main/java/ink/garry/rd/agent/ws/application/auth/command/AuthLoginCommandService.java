package ink.garry.rd.agent.ws.application.auth.command;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.LoginSecurityProperties;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginParamDTO;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginResultDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.factory.UserFactory;
import ink.garry.rd.agent.ws.domain.user.valueobject.UserStatus;
import ink.garry.rd.agent.ws.facade.auth.token.LocalTokenIssuer;
import ink.garry.rd.agent.ws.facade.auth.token.UserClaims;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 用户名密码登录应用服务。
 * <p>连续失败达阈值后须通过滑块人机校验（按用户名 + IP 计数）。</p>
 */
@Service
public class AuthLoginCommandService {

    @Resource
    private UserQueryService userQueryService;
    @Resource
    private UserFactory userFactory;
    @Resource
    private LocalTokenIssuer localTokenIssuer;
    @Resource
    private AuthzCommandService authzCommandService;
    @Resource
    private LoginFailCounterService loginFailCounterService;
    @Resource
    private SliderCaptchaService sliderCaptchaService;
    @Resource
    private LoginSecurityProperties loginSecurityProperties;

    /**
     * 登录：可选滑块校验 → 校验凭据 → 签发 JWT。
     *
     * @param param 登录参数（含 clientIp / captcha）
     * @return 含 token 的结果（adapter 写 Cookie）
     */
    public LoginResultDTO login(LoginParamDTO param) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getUsername(), "用户名不能为空");
        Assert.notBlank(param.getPassword(), "密码不能为空");

        String username = param.getUsername().trim();
        String clientIp = StrUtil.blankToDefault(param.getClientIp(), "unknown");

        if (loginFailCounterService.requiresCaptcha(username, clientIp)) {
            sliderCaptchaService.verifyOrThrow(param.getCaptchaId(), param.getSlideX());
        }

        String num = userQueryService.findNumByUsername(username);
        if (StrUtil.isBlank(num)) {
            onPasswordFailed(username, clientIp);
            throw new IllegalStateException("unreachable");
        }
        User user = userFactory.createByNum(num);
        if (user == null) {
            onPasswordFailed(username, clientIp);
            throw new IllegalStateException("unreachable");
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(BizCode.USER_DISABLED.getCode(),
                    BizCode.USER_DISABLED.getMessage());
        }
        if (!user.verifyPassword(param.getPassword())) {
            onPasswordFailed(username, clientIp);
        }

        loginFailCounterService.clear(username, clientIp);

        authzCommandService.ensureDefaultPlatformRole(user.getNum());

        String token = localTokenIssuer.issue(UserClaims.builder()
                .uuid(user.getNum())
                .account(user.getUsername())
                .roles(Collections.emptyList())
                .build());

        LoginResultDTO result = new LoginResultDTO();
        result.setUserNum(user.getNum());
        result.setUsername(user.getUsername());
        result.setToken(token);
        return result;
    }

    private void onPasswordFailed(String username, String clientIp) {
        long count = loginFailCounterService.increment(username, clientIp);
        if (count >= loginSecurityProperties.getFailThreshold()) {
            throw new BusinessException(BizCode.LOGIN_CAPTCHA_REQUIRED.getCode(),
                    "用户名或密码错误，请完成滑块验证后重试");
        }
        throw new BusinessException(BizCode.USER_LOGIN_FAILED.getCode(),
                BizCode.USER_LOGIN_FAILED.getMessage());
    }
}
