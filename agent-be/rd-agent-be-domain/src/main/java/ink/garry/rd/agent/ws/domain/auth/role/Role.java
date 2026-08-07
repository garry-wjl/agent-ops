package ink.garry.rd.agent.ws.domain.auth.role;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.dto.RoleDomainEventDTO;
import ink.garry.rd.agent.ws.domain.auth.role.gateway.RoleGateway;
import ink.garry.rd.agent.ws.domain.auth.role.repository.RoleRepository;
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
 * Role 聚合根。
 * <p>
 * 平台角色（{@code RL-PLATFORM-*}）与空间角色（{@code RL-SPACE-*}）统一在此聚合内表达，区分由 {@link RoleScope}
 * 与 {@link #workspaceNum} 携带。内置角色（{@link #builtin}=true）由 Flyway 直接写入，运行时禁止经聚合修改 / 删除。
 * </p>
 *
 * <p><b>领域动作仅 save / delete / updatePermissions 三个</b>：</p>
 * <ul>
 *   <li>name / description 等普通字段由 application 层 setter + {@link #save(String)} 完成（非状态机变化统一走 save）。</li>
 *   <li>{@link #updatePermissions(Set, String)} 作为权限码集合的强校验入口保留，内部委托 save。</li>
 * </ul>
 *
 * <p>事件：{@link DomainEventConstant#ROLE_CREATED}（首次落库）/ {@link DomainEventConstant#ROLE_UPDATED}（已存在 save）
 * / {@link DomainEventConstant#ROLE_DELETED}（软删除）。</p>
 */
@Getter
@Setter
public class Role extends DomainEntity {

    /** 角色名长度上限（与 client.AuthzConstants 对齐） */
    private static final int NAME_MAX_LENGTH = 64;
    /** 角色描述长度上限 */
    private static final int DESC_MAX_LENGTH = 200;

    // ---- 业务字段 ----

    /** 角色业务编号（前缀 RL-PLATFORM- / RL-SPACE-，由 Gateway 生成） */
    private String num;

    /** 角色名 */
    private String name;

    /** 角色描述（可空） */
    private String description;

    /** 作用域 */
    private RoleScope scope;

    /** 归属空间编号；scope=SPACE 且非内置模板时必填，scope=PLATFORM 时必须为 null */
    private String workspaceNum;

    /** 是否内置角色（true 时禁止 updatePermissions / delete / save 修改字段） */
    private Boolean builtin;

    /** 权限码集合（与 PermissionRegistry 中的 code 对齐） */
    private Set<String> permissionCodes;

    /** 状态：ENABLED / DISABLED；默认 ENABLED */
    private String status;

    // ---- 装配依赖 ----

    /** 装配依赖：Role 仓储，承担 save / findByNum / deleteByNum */
    private transient RoleRepository roleRepository;
    /** 装配依赖：Role 网关（业务编号生成 / 唯一性预检 / 绑定数统计） */
    private transient RoleGateway roleGateway;
    /** 装配依赖：领域事件发布器 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造，infra Repository 重建聚合时使用 */
    public Role() {
    }

    /**
     * 必填字段 + 装配依赖构造方法（由 RoleFactory.buildRole 创建新聚合时调用）。
     *
     * @param name                角色名
     * @param description         角色描述（可空）
     * @param scope               作用域
     * @param workspaceNum        归属空间编号（scope=PLATFORM 时传 null）
     * @param permissionCodes     权限码集合（必填且非空，全部存在于 PermissionRegistry）
     * @param roleRepository      Role 仓储
     * @param roleGateway         Role 网关
     * @param domainEventPublisher 领域事件发布器
     */
    public Role(String name,
                String description,
                RoleScope scope,
                String workspaceNum,
                Set<String> permissionCodes,
                RoleRepository roleRepository,
                RoleGateway roleGateway,
                DomainEventPublisher domainEventPublisher) {
        this.name = name;
        this.description = description;
        this.scope = scope;
        this.workspaceNum = workspaceNum;
        this.permissionCodes = permissionCodes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissionCodes);
        this.builtin = Boolean.FALSE;
        this.status = "ENABLED";
        this.roleRepository = roleRepository;
        this.roleGateway = roleGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：
     * <ul>
     *   <li>name 非空、长度 [1, 64]</li>
     *   <li>description 长度 ≤ 200</li>
     *   <li>scope=SPACE 时 workspaceNum 必填（内置模板例外，由 Flyway 直接写）</li>
     *   <li>scope=PLATFORM 时 workspaceNum 必须为 null</li>
     *   <li>permissionCodes 不为空</li>
     * </ul>
     * <p>builtin 角色的「不可修改」由 {@link #save(String)} / {@link #delete(String)} / {@link #updatePermissions(Set, String)} 在入口处强制校验。</p>
     */
    @Override
    public void domainValidate() {
        if (StrUtil.isBlank(name) || name.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(AuthzErrorCode.ROLE_NAME_INVALID.getCode(),
                    AuthzErrorCode.ROLE_NAME_INVALID.format());
        }
        if (description != null && description.length() > DESC_MAX_LENGTH) {
            throw new BusinessException(AuthzErrorCode.ROLE_DESC_TOO_LONG.getCode(),
                    AuthzErrorCode.ROLE_DESC_TOO_LONG.format());
        }
        Assert.notNull(scope, "角色作用域不能为空");
        if (scope == RoleScope.PLATFORM && workspaceNum != null) {
            throw new BusinessException(AuthzErrorCode.ROLE_SCOPE_INVALID.getCode(),
                    AuthzErrorCode.ROLE_SCOPE_INVALID.format());
        }
        // 注：内置 SPACE 模板（builtin=true）允许 workspaceNum 为 null，由 Flyway 写；
        // 自定义 SPACE 角色（builtin=false）必须有 workspaceNum
        if (scope == RoleScope.SPACE && Boolean.FALSE.equals(builtin) && StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(AuthzErrorCode.ROLE_SCOPE_INVALID.getCode(),
                    AuthzErrorCode.ROLE_SCOPE_INVALID.format());
        }
        if (CollUtil.isEmpty(permissionCodes)) {
            throw new BusinessException(AuthzErrorCode.PERMISSION_NOT_FOUND.getCode(),
                    AuthzErrorCode.PERMISSION_NOT_FOUND.format("<empty>"));
        }
    }

    /**
     * 保存 / 编辑角色（创建与编辑统一入口）。
     * <p>
     * 编辑时由 application 层从 Factory 取出聚合后 setter 修改 name / description / permissionCodes
     * 再调用本方法，整聚合覆盖落库。
     * </p>
     *
     * <p>六步顺序：(1) builtin 守卫 → (2) 初始化审计 → (3) num 生成 → (4) domainValidate
     * → (5) Repository.save → (6) 发 CREATED / UPDATED 事件。</p>
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void save(String operatorId) {
        // 1. builtin 角色禁止经 save 修改字段
        if (Boolean.TRUE.equals(this.builtin) && this.getId() != null) {
            throw new BusinessException(AuthzErrorCode.BUILTIN_ROLE_READONLY.getCode(),
                    AuthzErrorCode.BUILTIN_ROLE_READONLY.format());
        }

        // 2. 初始化审计
        this.initialize(operatorId);

        // 3. 默认值 + num 生成
        if (this.builtin == null) {
            this.builtin = Boolean.FALSE;
        }
        if (this.status == null) {
            this.status = "ENABLED";
        }
        boolean isNew = this.getId() == null;
        if (StrUtil.isBlank(this.num)) {
            this.num = roleGateway.generateRoleNum(this.scope, this.workspaceNum);
        }

        // 4. 领域校验
        this.validate();

        // 5. 持久化
        roleRepository.save(this);

        // 6. 发布事件
        publishEvent(isNew ? DomainEventConstant.ROLE_CREATED : DomainEventConstant.ROLE_UPDATED, operatorId);
    }

    /**
     * 逻辑删除角色。
     * <p>builtin 角色禁止删除；application 层须先调 {@link RoleGateway#countAssignedUsers(String)} 校验 ==0。</p>
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void delete(String operatorId) {
        if (Boolean.TRUE.equals(this.builtin)) {
            throw new BusinessException(AuthzErrorCode.BUILTIN_ROLE_READONLY.getCode(),
                    AuthzErrorCode.BUILTIN_ROLE_READONLY.format());
        }
        this.initialize(operatorId);
        this.deleted = 1;
        this.validate();
        roleRepository.deleteByNum(this.num);
        publishEvent(DomainEventConstant.ROLE_DELETED, operatorId);
    }

    /**
     * 更新权限码集合并立即落库（便捷动作）。
     * <p>permissionCodes 合法性由 application 层在调用前对照 PermissionRegistry 过滤；本方法仅做空集兜底。</p>
     *
     * @param codes      新权限码集合
     * @param operatorId 操作人工号
     */
    public void updatePermissions(Set<String> codes, String operatorId) {
        if (Boolean.TRUE.equals(this.builtin)) {
            throw new BusinessException(AuthzErrorCode.BUILTIN_ROLE_READONLY.getCode(),
                    AuthzErrorCode.BUILTIN_ROLE_READONLY.format());
        }
        this.permissionCodes = codes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(codes);
        this.save(operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送。
     *
     * @param type       事件类型
     * @param operatorId 操作人工号
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(RoleDomainEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
