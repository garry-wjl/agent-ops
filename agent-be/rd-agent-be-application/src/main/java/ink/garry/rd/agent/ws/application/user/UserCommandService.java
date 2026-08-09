package ink.garry.rd.agent.ws.application.user;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.user.constant.UserConstants;
import ink.garry.rd.agent.ws.client.user.dto.UserCreateParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDetailDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserPlatformRolesParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserResetPasswordParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.factory.UserFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * User 写侧应用服务。
 */
@Slf4j
@Service
public class UserCommandService {

    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private UserFactory userFactory;
    @Resource
    private UserQueryService userQueryService;
    @Resource
    private AuthzCommandService authzCommandService;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 新建用户。
     *
     * @param param      创建参数
     * @param operatorId 操作人
     * @return 用户摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDTO createUser(UserCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateUsername(param.getUsername());
        validateEmail(param.getEmail());
        validatePassword(param.getPassword());
        validateRemark(param.getRemark());

        String lockKey = LockKeyConstant.USER_CREATE_LOCK_PREFIX + param.getUsername();
        return runWithLock(lockKey, () -> {
            User user = userFactory.create(
                    param.getUsername().trim(),
                    param.getEmail().trim(),
                    param.getRemark(),
                    param.getPassword());
            user.save(operatorId);
            // 默认平台普通用户角色
            authzCommandService.ensureDefaultPlatformRole(user.getNum());
            UserDetailDTO detail = userQueryService.getUser(user.getNum());
            return toSummary(detail);
        });
    }

    /**
     * 更新用户资料。
     *
     * @param param      更新参数
     * @param operatorId 操作人
     * @return 用户摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDTO updateUser(UserUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getNum(), "用户编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateUsername(param.getUsername());
        validateEmail(param.getEmail());
        validateRemark(param.getRemark());

        String lockKey = LockKeyConstant.USER_COMMAND_LOCK_PREFIX + param.getNum();
        return runWithLock(lockKey, () -> {
            User user = requireUser(param.getNum());
            user.updateProfile(param.getUsername().trim(), param.getEmail().trim(),
                    param.getRemark(), operatorId);
            return toSummary(userQueryService.getUser(user.getNum()));
        });
    }

    /**
     * 启用用户。
     *
     * @param num        用户编号
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(String num, String operatorId) {
        Assert.notBlank(num, "用户编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        runWithLock(LockKeyConstant.USER_COMMAND_LOCK_PREFIX + num, () -> {
            requireUser(num).enable(operatorId);
            return null;
        });
    }

    /**
     * 禁用用户。
     *
     * @param num        用户编号
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(String num, String operatorId) {
        Assert.notBlank(num, "用户编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        runWithLock(LockKeyConstant.USER_COMMAND_LOCK_PREFIX + num, () -> {
            requireUser(num).disable(operatorId);
            return null;
        });
    }

    /**
     * 重置密码。
     *
     * @param param      重置参数
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UserResetPasswordParamDTO param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getNum(), "用户编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validatePassword(param.getPassword());
        runWithLock(LockKeyConstant.USER_COMMAND_LOCK_PREFIX + param.getNum(), () -> {
            requireUser(param.getNum()).resetPassword(param.getPassword(), operatorId);
            return null;
        });
    }

    /**
     * 覆盖保存平台角色。
     *
     * @param param      角色参数
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePlatformRoles(UserPlatformRolesParamDTO param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getNum(), "用户编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        // 确保用户存在
        userQueryService.getUser(param.getNum());
        Set<String> roleNums = param.getRoleNums() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(param.getRoleNums());
        authzCommandService.saveUserPlatformRoles(param.getNum(), roleNums, operatorId);
    }

    private User requireUser(String num) {
        User user = userFactory.createByNum(num);
        if (user == null) {
            throw new BusinessException(BizCode.USER_NOT_FOUND.getCode(), BizCode.USER_NOT_FOUND.getMessage());
        }
        return user;
    }

    private static UserDTO toSummary(UserDetailDTO detail) {
        UserDTO dto = new UserDTO();
        dto.setNum(detail.getNum());
        dto.setUsername(detail.getUsername());
        dto.setEmail(detail.getEmail());
        dto.setRemark(detail.getRemark());
        dto.setStatus(detail.getStatus());
        return dto;
    }

    private static void validateUsername(String username) {
        Assert.notBlank(username, "用户名不能为空");
        Assert.isTrue(username.length() <= UserConstants.USERNAME_MAX_LENGTH, "用户名长度不能超过 64");
        Assert.isTrue(ReUtil.isMatch(UserConstants.USERNAME_PATTERN, username.trim()),
                "用户名仅允许字母数字及 ._-");
    }

    private static void validateEmail(String email) {
        Assert.notBlank(email, "邮箱不能为空");
        Assert.isTrue(email.length() <= UserConstants.EMAIL_MAX_LENGTH, "邮箱长度不能超过 128");
        Assert.isTrue(email.contains("@"), "邮箱格式不正确");
    }

    private static void validatePassword(String password) {
        Assert.notBlank(password, "密码不能为空");
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH) {
            throw new BusinessException(BizCode.USER_PASSWORD_INVALID.getCode(),
                    BizCode.USER_PASSWORD_INVALID.getMessage());
        }
    }

    private static void validateRemark(String remark) {
        Assert.isTrue(StrUtil.isBlank(remark) || remark.length() <= UserConstants.REMARK_MAX_LENGTH,
                "备注长度不能超过 512");
    }

    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "操作冲突，请稍后重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "操作被中断");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
