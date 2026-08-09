package ink.garry.rd.agent.ws.application.auth.command;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
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

    /**
     * 登录：校验凭据并签发 JWT。
     *
     * @param param 登录参数
     * @return 含 token 的结果（adapter 写 Cookie）
     */
    public LoginResultDTO login(LoginParamDTO param) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getUsername(), "用户名不能为空");
        Assert.notBlank(param.getPassword(), "密码不能为空");

        String num = userQueryService.findNumByUsername(param.getUsername().trim());
        if (StrUtil.isBlank(num)) {
            throw new BusinessException(BizCode.USER_LOGIN_FAILED.getCode(),
                    BizCode.USER_LOGIN_FAILED.getMessage());
        }
        User user = userFactory.createByNum(num);
        if (user == null) {
            throw new BusinessException(BizCode.USER_LOGIN_FAILED.getCode(),
                    BizCode.USER_LOGIN_FAILED.getMessage());
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new BusinessException(BizCode.USER_DISABLED.getCode(),
                    BizCode.USER_DISABLED.getMessage());
        }
        if (!user.verifyPassword(param.getPassword())) {
            throw new BusinessException(BizCode.USER_LOGIN_FAILED.getCode(),
                    BizCode.USER_LOGIN_FAILED.getMessage());
        }

        // 确保至少有默认平台角色
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
}
