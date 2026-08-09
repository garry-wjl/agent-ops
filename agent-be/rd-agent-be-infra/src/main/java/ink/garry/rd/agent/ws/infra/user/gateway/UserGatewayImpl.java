package ink.garry.rd.agent.ws.infra.user.gateway;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.user.gateway.UserGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.user.entity.UserEntity;
import ink.garry.rd.agent.ws.infra.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link UserGateway} 实现：编号、BCrypt、唯一性与禁用保护。
 */
@Component
public class UserGatewayImpl implements UserGateway {

    private static final String PREFIX = "USR-";
    private static final int SUFFIX_LENGTH = 12;
    private static final int CODE_CONFLICT = 1005;
    private static final int CODE_LAST_ADMIN = 1104;
    private static final int CODE_PASSWORD_INVALID = 1105;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    @Override
    public String generateUserNum() {
        for (int i = 0; i < 8; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);
            String num = PREFIX + suffix;
            if (userMapper.findByNum(num) == null) {
                return num;
            }
        }
        throw new BusinessException(CODE_CONFLICT, "生成用户编号失败，请重试");
    }

    @Override
    public String hashPassword(String rawPassword) {
        Assert.notBlank(rawPassword, "密码不能为空");
        if (rawPassword.length() < 8 || rawPassword.length() > 64) {
            throw new BusinessException(CODE_PASSWORD_INVALID, "密码长度须在 8~64 之间");
        }
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matchesPassword(String rawPassword, String passwordHash) {
        if (StrUtil.isBlank(rawPassword) || StrUtil.isBlank(passwordHash)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void assertUsernameUnique(String username, String excludeNum) {
        Assert.notBlank(username, "用户名不能为空");
        UserEntity existing = userMapper.findByUsername(username);
        if (existing != null && !existing.getNum().equals(excludeNum)) {
            throw new BusinessException(CODE_CONFLICT, "用户名已存在");
        }
    }

    @Override
    public void assertEmailUnique(String email, String excludeNum) {
        Assert.notBlank(email, "邮箱不能为空");
        UserEntity existing = userMapper.findByEmail(email);
        if (existing != null && !existing.getNum().equals(excludeNum)) {
            throw new BusinessException(CODE_CONFLICT, "邮箱已存在");
        }
    }

    @Override
    public void assertCanDisable(String userNum) {
        Assert.notBlank(userNum, "用户编号不能为空");
        long allEnabledAdmins = userMapper.countEnabledPlatformAdmins(null);
        long othersEnabledAdmins = userMapper.countEnabledPlatformAdmins(userNum);
        boolean selfIsEnabledAdmin = allEnabledAdmins > othersEnabledAdmins;
        if (selfIsEnabledAdmin && othersEnabledAdmins == 0) {
            throw new BusinessException(CODE_LAST_ADMIN, "不能禁用最后一名平台管理员");
        }
    }
}
