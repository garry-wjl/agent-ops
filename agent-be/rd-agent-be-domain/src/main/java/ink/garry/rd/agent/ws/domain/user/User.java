package ink.garry.rd.agent.ws.domain.user;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.user.gateway.UserGateway;
import ink.garry.rd.agent.ws.domain.user.repository.UserRepository;
import ink.garry.rd.agent.ws.domain.user.valueobject.UserStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * User 聚合根：平台用户主数据（登录名 / 邮箱 / 启停 / 密码哈希）。
 */
@Getter
@Setter
public class User extends DomainEntity {

    private static final int USERNAME_MAX = 64;
    private static final int EMAIL_MAX = 128;
    private static final int REMARK_MAX = 512;
    private static final String USERNAME_REGEX = "^[A-Za-z0-9._-]+$";

    /** 业务编号 USR-… */
    private String num;

    /** 登录用户名（全局唯一，可改）。 */
    private String username;

    /** 邮箱（全局唯一）。 */
    private String email;

    /** 备注。 */
    private String remark;

    /** 启停状态。 */
    private UserStatus status;

    /** BCrypt 密码哈希（永不回传前端）。 */
    private String passwordHash;

    private transient UserRepository userRepository;
    private transient UserGateway userGateway;
    private transient DomainEventPublisher domainEventPublisher;

    public User() {
    }

    /**
     * 新建聚合构造：必填字段 + 装配依赖；num / status 由工厂或 save 赋值。
     *
     * @param username             用户名
     * @param email                邮箱
     * @param remark               备注
     * @param passwordHash         已哈希密码
     * @param userRepository       仓储
     * @param userGateway          网关
     * @param domainEventPublisher 事件发布器
     */
    public User(String username,
                String email,
                String remark,
                String passwordHash,
                UserRepository userRepository,
                UserGateway userGateway,
                DomainEventPublisher domainEventPublisher) {
        this.username = username;
        this.email = email;
        this.remark = remark;
        this.passwordHash = passwordHash;
        this.userRepository = userRepository;
        this.userGateway = userGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Override
    public void domainValidate() {
        Assert.notBlank(username, "用户名不能为空");
        Assert.isTrue(username.length() <= USERNAME_MAX, "用户名长度不能超过 64");
        Assert.isTrue(ReUtil.isMatch(USERNAME_REGEX, username), "用户名仅允许字母数字及 ._-");
        Assert.notBlank(email, "邮箱不能为空");
        Assert.isTrue(email.length() <= EMAIL_MAX, "邮箱长度不能超过 128");
        Assert.isTrue(email.contains("@"), "邮箱格式不正确");
        Assert.isTrue(remark == null || remark.length() <= REMARK_MAX, "备注长度不能超过 512");
        Assert.notNull(status, "用户状态不能为空");
        Assert.notBlank(passwordHash, "密码哈希不能为空");
        Assert.notBlank(num, "用户业务编号不能为空");
    }

    /**
     * 持久化新建 / 更新（六步顺序）。
     *
     * @param operatorId 操作人
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计
        this.initialize(operatorId);

        // 2. save 无额外前置状态约束

        // 3. 赋值：status 兜底 ENABLED；num 空则网关生成；唯一性校验
        if (this.status == null) {
            this.status = UserStatus.ENABLED;
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = userGateway.generateUserNum();
        }
        userGateway.assertUsernameUnique(this.username, this.num);
        userGateway.assertEmailUnique(this.email, this.num);

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        userRepository.save(this);

        // 6. 发布事件
        publish(DomainEventConstant.USER_SAVED, operatorId);
    }

    /**
     * 软删除用户。
     *
     * @param operatorId 操作人
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 存在性由调用方保证
        Assert.notBlank(this.num, "用户业务编号不能为空");
        // 3. 标记删除
        this.deleted = 1;
        // 4. 校验审计字段
        this.validate();
        // 5. 软删
        userRepository.deleteByNum(this.num);
        // 6. 事件
        publish(DomainEventConstant.USER_DELETED, operatorId);
    }

    /**
     * 更新用户名 / 邮箱 / 备注。
     *
     * @param newUsername 新用户名
     * @param newEmail    新邮箱
     * @param newRemark   新备注
     * @param operatorId  操作人
     */
    public void updateProfile(String newUsername, String newEmail, String newRemark, String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 无状态前置（启用/禁用均可改资料）
        // 3. 赋值 + 唯一性
        Assert.notBlank(newUsername, "用户名不能为空");
        Assert.notBlank(newEmail, "邮箱不能为空");
        userGateway.assertUsernameUnique(newUsername, this.num);
        userGateway.assertEmailUnique(newEmail, this.num);
        this.username = newUsername;
        this.email = newEmail;
        this.remark = newRemark;
        // 4. 校验
        this.validate();
        // 5. 持久化
        userRepository.save(this);
        // 6. 事件
        publish(DomainEventConstant.USER_PROFILE_UPDATED, operatorId);
    }

    /**
     * 启用用户。
     *
     * @param operatorId 操作人
     */
    public void enable(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 前置：当前须为 DISABLED
        Assert.isTrue(this.status == UserStatus.DISABLED, "仅禁用用户可启用");
        // 3. 赋值
        this.status = UserStatus.ENABLED;
        // 4. 校验
        this.validate();
        // 5. 持久化
        userRepository.save(this);
        // 6. 事件
        publish(DomainEventConstant.USER_ENABLED, operatorId);
    }

    /**
     * 禁用用户。
     *
     * @param operatorId 操作人
     */
    public void disable(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 前置：当前须为 ENABLED；且非最后一名平台管理员
        Assert.isTrue(this.status == UserStatus.ENABLED, "仅启用用户可禁用");
        userGateway.assertCanDisable(this.num);
        // 3. 赋值
        this.status = UserStatus.DISABLED;
        // 4. 校验
        this.validate();
        // 5. 持久化
        userRepository.save(this);
        // 6. 事件
        publish(DomainEventConstant.USER_DISABLED, operatorId);
    }

    /**
     * 校验登录密码（不落库、不发事件）。
     *
     * @param rawPassword 明文密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String rawPassword) {
        return userGateway.matchesPassword(rawPassword, this.passwordHash);
    }

    /**
     * 管理员重置密码。
     *
     * @param rawPassword 明文新密码
     * @param operatorId  操作人
     */
    public void resetPassword(String rawPassword, String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);
        // 2. 前置
        Assert.notBlank(rawPassword, "新密码不能为空");
        // 3. 哈希赋值
        this.passwordHash = userGateway.hashPassword(rawPassword);
        // 4. 校验
        this.validate();
        // 5. 持久化
        userRepository.save(this);
        // 6. 事件（载荷不含密码）
        publish(DomainEventConstant.USER_PASSWORD_RESET, operatorId);
    }

    private void publish(String type, String operatorId) {
        Map<String, Object> data = new HashMap<>();
        data.put("num", this.num);
        data.put("username", this.username);
        data.put("status", this.status == null ? null : this.status.name());
        domainEventPublisher.send(DomainEventDTO.builder()
                .type(type)
                .id(UUID.randomUUID().toString())
                .data(data)
                .sender(operatorId)
                .build());
    }
}
