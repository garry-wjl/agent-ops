package ink.garry.rd.agent.ws.domain.auth.userrolebinding;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.dto.UserRoleBindingEventDTO;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.gateway.UserRoleBindingGateway;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.AuthzErrorCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 用户角色绑定聚合根。
 * <p>核心属性：</p>
 * <ul>
 *   <li>{@link #num}：业务编码</li>
 *   <li>{@link #userId}：用户工号</li>
 *   <li>{@link #roleType}：绑定类型（平台 / 空间）</li>
 *   <li>{@link #roleNums}：该 (user, scope) 下的全部角色 num 集合</li>
 * </ul>
 *
 * <p>领域动作仅 {@link #save(String)} / {@link #delete(String)}。</p>
 * <p>save 语义：整聚合按 (workspaceNum, userId) 覆盖落库（物理 DELETE 旧行 + INSERT 新行）；
 * 若 roleNums 为空，等价于 delete。</p>
 */
@Getter
@Setter
public class UserRoleBinding extends DomainEntity {

    /** 业务编码 */
    private String num;

    /** 绑定类型 */
    private RoleBindingType roleType;

    /** 用户工号 */
    private String userId;

    /** 工作空间业务编号（PLATFORM 时为 SYSTEM；SPACE 时为具体编号） */
    private String workspaceNum;

    /** 当前持有的角色 num 集合 */
    private Set<String> roleNums;

    // ---- 装配依赖 ----
    private transient UserRoleBindingRepository userRoleBindingRepository;
    private transient UserRoleBindingGateway userRoleBindingGateway;
    private transient DomainEventPublisher domainEventPublisher;

    public UserRoleBinding() {
    }

    public UserRoleBinding(RoleBindingType roleType,
                           String userId,
                           String workspaceNum,
                           Set<String> roleNums,
                           UserRoleBindingRepository userRoleBindingRepository,
                           UserRoleBindingGateway userRoleBindingGateway,
                           DomainEventPublisher domainEventPublisher) {
        this.roleType = roleType;
        this.userId = userId;
        this.workspaceNum = workspaceNum;
        this.roleNums = roleNums == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roleNums);
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.userRoleBindingGateway = userRoleBindingGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：
     * <ul>
     *   <li>userId 非空</li>
     *   <li>roleType 非空</li>
     *   <li>workspaceNum 必填（PLATFORM 必须为 SYSTEM；SPACE 必须为具体编号）</li>
     *   <li>roleNums 中元素数量 ≤ {@link AuthzDomainConstants#USER_ROLE_PER_WORKSPACE_LIMIT}</li>
     * </ul>
     */
    @Override
    public void domainValidate() {
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(AuthzErrorCode.ROLE_ASSIGNMENT_WORKSPACE_REQUIRED.getCode(),
                    "userId 不能为空");
        }
        Assert.notNull(roleType, "roleType 不能为空");
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(AuthzErrorCode.ROLE_ASSIGNMENT_WORKSPACE_REQUIRED.getCode(),
                    AuthzErrorCode.ROLE_ASSIGNMENT_WORKSPACE_REQUIRED.format());
        }
        if (roleType == RoleBindingType.PLATFORM
                && !AuthzDomainConstants.PLATFORM_WORKSPACE_NUM.equals(workspaceNum)) {
            throw new BusinessException(AuthzErrorCode.ROLE_SCOPE_INVALID.getCode(),
                    "PLATFORM 绑定的 workspaceNum 必须为 SYSTEM");
        }
        if (roleType == RoleBindingType.SPACE
                && AuthzDomainConstants.PLATFORM_WORKSPACE_NUM.equals(workspaceNum)) {
            throw new BusinessException(AuthzErrorCode.ROLE_SCOPE_INVALID.getCode(),
                    "SPACE 绑定的 workspaceNum 不可为 SYSTEM");
        }
        if (roleNums != null && roleNums.size() > AuthzDomainConstants.USER_ROLE_PER_WORKSPACE_LIMIT) {
            throw new BusinessException(AuthzErrorCode.USER_ROLE_LIMIT_EXCEEDED.getCode(),
                    AuthzErrorCode.USER_ROLE_LIMIT_EXCEEDED.format(AuthzDomainConstants.USER_ROLE_PER_WORKSPACE_LIMIT));
        }
    }

    /**
     * 保存：roleNums 空集合等价于 {@link #delete(String)}（无意义的"空绑定"不落库）；
     * 否则按 (workspaceNum, userId) 覆盖式写入。
     */
    @Override
    public void save(String operatorId) {
        if (roleNums == null || roleNums.isEmpty()) {
            // 空 roleNums 等价于解除该用户在该上下文下的全部角色绑定
            delete(operatorId);
            return;
        }
        this.initialize(operatorId);
        if (StrUtil.isBlank(this.num)) {
            this.num = userRoleBindingGateway.generateBindingNum(roleType, workspaceNum, userId);
        }
        this.validate();
        userRoleBindingRepository.save(this);
        publishEvent(DomainEventConstant.USER_ROLE_BOUND, operatorId);
    }

    /**
     * 解除该 (user, scope) 下全部角色绑定。
     */
    @Override
    public void delete(String operatorId) {
        this.initialize(operatorId);
        if (StrUtil.isBlank(this.num)) {
            this.num = userRoleBindingGateway.generateBindingNum(roleType, workspaceNum, userId);
        }
        Set<String> snapshot = this.roleNums == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(this.roleNums);
        userRoleBindingRepository.deleteByNum(this.num);
        // 事件载荷里 roleNums 保留删除前的快照，便于订阅方比对
        this.roleNums = snapshot;
        publishEvent(DomainEventConstant.USER_ROLE_UNBOUND, operatorId);
    }

    // ---- 私有辅助 ----

    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(UserRoleBindingEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
