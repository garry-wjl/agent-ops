package ink.garry.rd.agent.ws.application.auth.command;

import ink.garry.rd.agent.ws.application.auth.LoginSecurityProperties;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.client.auth.login.dto.LoginParamDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.factory.UserFactory;
import ink.garry.rd.agent.ws.domain.user.valueobject.UserStatus;
import ink.garry.rd.agent.ws.facade.auth.token.LocalTokenIssuer;
import ink.garry.rd.agent.ws.facade.auth.token.UserClaims;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthLoginCommandService} 滑块门禁与失败计数单测。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthLoginCommandServiceTest {

    @Mock
    private UserQueryService userQueryService;
    @Mock
    private UserFactory userFactory;
    @Mock
    private LocalTokenIssuer localTokenIssuer;
    @Mock
    private AuthzCommandService authzCommandService;
    @Mock
    private LoginFailCounterService loginFailCounterService;
    @Mock
    private SliderCaptchaService sliderCaptchaService;
    @Mock
    private LoginSecurityProperties loginSecurityProperties;
    @Mock
    private User user;

    @InjectMocks
    private AuthLoginCommandService service;

    @BeforeEach
    void stubThreshold() {
        when(loginSecurityProperties.getFailThreshold()).thenReturn(3);
    }

    @Test
    void login_whenCaptchaRequired_verifiesSlider() {
        when(loginFailCounterService.requiresCaptcha("alice", "1.1.1.1")).thenReturn(true);
        doNothing().when(sliderCaptchaService).verifyOrThrow("cid", 120);
        when(userQueryService.findNumByUsername("alice")).thenReturn("USR-1");
        when(userFactory.createByNum("USR-1")).thenReturn(user);
        when(user.getStatus()).thenReturn(UserStatus.ENABLED);
        when(user.verifyPassword("secret")).thenReturn(true);
        when(user.getNum()).thenReturn("USR-1");
        when(user.getUsername()).thenReturn("alice");
        when(localTokenIssuer.issue(any(UserClaims.class))).thenReturn("tok");

        LoginParamDTO param = new LoginParamDTO();
        param.setUsername("alice");
        param.setPassword("secret");
        param.setClientIp("1.1.1.1");
        param.setCaptchaId("cid");
        param.setSlideX(120);

        service.login(param);

        verify(sliderCaptchaService).verifyOrThrow("cid", 120);
        verify(loginFailCounterService).clear("alice", "1.1.1.1");
    }

    @Test
    void login_whenCaptchaRequiredButMissing_throws() {
        when(loginFailCounterService.requiresCaptcha("alice", "1.1.1.1")).thenReturn(true);
        doThrow(new BusinessException(BizCode.LOGIN_CAPTCHA_REQUIRED.getCode(), "请完成滑块验证"))
                .when(sliderCaptchaService).verifyOrThrow(nullable(String.class), nullable(Integer.class));

        LoginParamDTO param = new LoginParamDTO();
        param.setUsername("alice");
        param.setPassword("secret");
        param.setClientIp("1.1.1.1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.login(param));
        assertEquals(BizCode.LOGIN_CAPTCHA_REQUIRED.getCode(), ex.getCode());
        verify(userQueryService, never()).findNumByUsername(anyString());
    }

    @Test
    void login_wrongPassword_thirdFail_returnsCaptchaRequired() {
        when(loginFailCounterService.requiresCaptcha("alice", "1.1.1.1")).thenReturn(false);
        when(userQueryService.findNumByUsername("alice")).thenReturn("USR-1");
        when(userFactory.createByNum("USR-1")).thenReturn(user);
        when(user.getStatus()).thenReturn(UserStatus.ENABLED);
        when(user.verifyPassword("bad")).thenReturn(false);
        when(loginFailCounterService.increment("alice", "1.1.1.1")).thenReturn(3L);

        LoginParamDTO param = new LoginParamDTO();
        param.setUsername("alice");
        param.setPassword("bad");
        param.setClientIp("1.1.1.1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.login(param));
        assertEquals(BizCode.LOGIN_CAPTCHA_REQUIRED.getCode(), ex.getCode());
    }
}
