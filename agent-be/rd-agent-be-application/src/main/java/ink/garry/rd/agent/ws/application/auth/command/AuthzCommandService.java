package ink.garry.rd.agent.ws.application.auth.command;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleCreateParam;
import ink.garry.rd.agent.ws.client.auth.role.param.RoleUpdateParam;
import ink.garry.rd.agent.ws.client.auth.roleassignment.param.PlatformRoleAssignParam;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.RoleScope;
import ink.garry.rd.agent.ws.domain.auth.role.Role;
import ink.garry.rd.agent.ws.domain.auth.role.factory.RoleFactory;
import ink.garry.rd.agent.ws.domain.auth.role.gateway.RoleGateway;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.factory.UserRoleBindingFactory;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.facade.exception.AuthzErrorCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.permission.PermissionRegistry;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 鉴权写侧应用服务（CommandService）。
 * <p>承载角色 CRUD、平台角色赋人、空间创建者绑定、用户-角色绑定（按 UserRoleBinding 聚合）。</p>
 */
@Slf4j
@Service
public class AuthzCommandService {

    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;
    private static final long COMMAND_LOCK_LEASE_SECONDS = 10L;

    @Resource
    private RoleFactory roleFactory;
    @Resource
    private RoleGateway roleGateway;
    @Resource
    private UserRoleBindingFactory userRoleBindingFactory;
    @Resource
    private UserRoleBindingRepository userRoleBindingRepository;
    @Resource
    private PermissionRegistry permissionRegistry;
    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // 角色：create / update / delete
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public String createRole(RoleCreateParam param, String workspaceNum, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getName(), "角色名不能为空");
        ensurePermissionsValid(param.getPermissionCodes(), RoleScope.SPACE);

        String lockKey = LockKeyConstant.AUTHZ_ROLE_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (roleGateway.isNameDuplicate(RoleScope.SPACE, workspaceNum, param.getName(), null)) {
                throw new BusinessException(AuthzErrorCode.ROLE_NAME_DUPLICATE.getCode(),
                        AuthzErrorCode.ROLE_NAME_DUPLICATE.format());
            }
            Role role = roleFactory.buildRole(
                    param.getName(), param.getDescription(),
                    RoleScope.SPACE, workspaceNum,
                    param.getPermissionCodes() == null
                            ? new LinkedHashSet<>() : new LinkedHashSet<>(param.getPermissionCodes()));
            role.save(operatorId);
            return role.getNum();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public String createPlatformRole(RoleCreateParam param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getName(), "角色名不能为空");
        ensurePermissionsValid(param.getPermissionCodes(), RoleScope.PLATFORM);

        String lockKey = LockKeyConstant.AUTHZ_ROLE_CREATE_LOCK_PREFIX + "PLATFORM:" + param.getName();
        return runWithLock(lockKey, () -> {
            if (roleGateway.isNameDuplicate(RoleScope.PLATFORM, null, param.getName(), null)) {
                throw new BusinessException(AuthzErrorCode.ROLE_NAME_DUPLICATE.getCode(),
                        AuthzErrorCode.ROLE_NAME_DUPLICATE.format());
            }
            Role role = roleFactory.buildRole(
                    param.getName(), param.getDescription(),
                    RoleScope.PLATFORM, null,
                    param.getPermissionCodes() == null
                            ? new LinkedHashSet<>() : new LinkedHashSet<>(param.getPermissionCodes()));
            role.save(operatorId);
            return role.getNum();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleUpdateParam param, String operatorId) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getRoleNum(), "roleNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getName(), "角色名不能为空");

        String lockKey = LockKeyConstant.AUTHZ_ROLE_UPDATE_LOCK_PREFIX + param.getRoleNum();
        runWithLock(lockKey, () -> {
            Role role = roleFactory.buildRoleByNum(param.getRoleNum());
            if (role == null) {
                throw new BusinessException(AuthzErrorCode.ROLE_NOT_FOUND.getCode(),
                        AuthzErrorCode.ROLE_NOT_FOUND.format(param.getRoleNum()));
            }
            if (Boolean.TRUE.equals(role.getBuiltin())) {
                throw new BusinessException(AuthzErrorCode.BUILTIN_ROLE_READONLY.getCode(),
                        AuthzErrorCode.BUILTIN_ROLE_READONLY.format());
            }
            ensurePermissionsValid(param.getPermissionCodes(), role.getScope());
            if (!StrUtil.equals(role.getName(), param.getName())
                    && roleGateway.isNameDuplicate(role.getScope(), role.getWorkspaceNum(),
                            param.getName(), role.getNum())) {
                throw new BusinessException(AuthzErrorCode.ROLE_NAME_DUPLICATE.getCode(),
                        AuthzErrorCode.ROLE_NAME_DUPLICATE.format());
            }
            role.setName(param.getName());
            role.setDescription(param.getDescription());
            role.setPermissionCodes(param.getPermissionCodes() == null
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(param.getPermissionCodes()));
            role.save(operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String roleNum, String operatorId) {
        Assert.notBlank(roleNum, "roleNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.AUTHZ_ROLE_DELETE_LOCK_PREFIX + roleNum;
        runWithLock(lockKey, () -> {
            long bound = roleGateway.countAssignedUsers(roleNum);
            if (bound > 0) {
                throw new BusinessException(AuthzErrorCode.ROLE_IN_USE.getCode(),
                        AuthzErrorCode.ROLE_IN_USE.format(bound));
            }
            Role role = roleFactory.buildRoleByNum(roleNum);
            if (role == null) {
                throw new BusinessException(AuthzErrorCode.ROLE_NOT_FOUND.getCode(),
                        AuthzErrorCode.ROLE_NOT_FOUND.format(roleNum));
            }
            role.delete(operatorId);
            return null;
        });
    }

    // ============================================================
    // 用户-角色绑定（UserRoleBinding 聚合）
    // ============================================================

    /**
     * 整空间用户-角色映射覆盖（编辑空间「保存」时调用）。
     * <p>新模型下：按 workspace 列出现有所有 UserRoleBinding，对照新 map：</p>
     * <ul>
     *   <li>map 中存在但 DB 没有 → buildBinding + save 新建</li>
     *   <li>DB 与 map 都有 → 加载 + setRoleNums 覆盖 + save</li>
     *   <li>DB 有但 map 没有 → delete（解除该用户在该空间下全部角色）</li>
     * </ul>
     *
     * @param workspaceNum 空间编号（不可为 SYSTEM）
     * @param userRoles    用户-角色映射（缺省时跳过；空 map 表示清空整空间所有用户绑定）
     * @param creatorEmpNo 空间创建者工号（用于 SPACE_ADMIN 保护；可空）
     * @param operatorId   操作人工号
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindUserRoles(String workspaceNum,
                              Map<String, Set<String>> userRoles,
                              String creatorEmpNo,
                              String operatorId) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        if (userRoles == null) {
            return;
        }
        if (AuthzConstants.PLATFORM_WORKSPACE_NUM.equals(workspaceNum)) {
            throw new BusinessException(AuthzErrorCode.ROLE_SCOPE_INVALID.getCode(),
                    "bindUserRoles 不能用于 SYSTEM workspace");
        }
        // SPACE_ADMIN 保护：若指定了 creator，必须在新 map 中持有 RL-SPACE-ADMIN
        if (StrUtil.isNotBlank(creatorEmpNo)) {
            Set<String> creatorRoles = userRoles.get(creatorEmpNo);
            if (creatorRoles == null || !creatorRoles.contains(AuthzConstants.ROLE_SPACE_ADMIN)) {
                throw new BusinessException(AuthzErrorCode.SPACE_ADMIN_UNREMOVABLE.getCode(),
                        AuthzErrorCode.SPACE_ADMIN_UNREMOVABLE.format());
            }
        }
        String lockKey = LockKeyConstant.AUTHZ_ASSIGNMENT_LOCK_PREFIX + workspaceNum;
        runWithLock(lockKey, () -> {
            List<UserRoleBinding> existing = userRoleBindingRepository.listByWorkspace(workspaceNum);
            Set<String> existingUserIds = new LinkedHashSet<>();
            for (UserRoleBinding b : existing) {
                existingUserIds.add(b.getUserId());
            }
            // upsert 入参中所有 user
            for (Map.Entry<String, Set<String>> entry : userRoles.entrySet()) {
                String userId = entry.getKey();
                Set<String> roleNums = entry.getValue();
                if (StrUtil.isBlank(userId)) continue;
                UserRoleBinding binding = userRoleBindingFactory.buildBindingByUser(userId, workspaceNum);
                if (binding == null) {
                    binding = userRoleBindingFactory.buildBinding(
                            RoleBindingType.SPACE, userId, workspaceNum, roleNums);
                } else {
                    binding.setRoleNums(roleNums == null
                            ? new LinkedHashSet<>() : new LinkedHashSet<>(roleNums));
                }
                binding.save(operatorId);
            }
            // 删除入参中没有的 user
            for (String userId : existingUserIds) {
                if (!userRoles.containsKey(userId)) {
                    UserRoleBinding binding = userRoleBindingFactory.buildBindingByUser(userId, workspaceNum);
                    if (binding != null) {
                        binding.delete(operatorId);
                    }
                }
            }
            return null;
        });
    }

    /** 工作空间创建后绑定创建者为 RL-SPACE-ADMIN（在 WorkspaceCommandService.create 末尾同事务调用）。 */
    public void bindCreatorAsSpaceAdmin(String workspaceNum, String creatorEmpNo) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(creatorEmpNo, "creatorEmpNo 不能为空");
        UserRoleBinding binding = userRoleBindingFactory.buildBinding(
                RoleBindingType.SPACE, creatorEmpNo, workspaceNum,
                Set.of(AuthzConstants.ROLE_SPACE_ADMIN));
        binding.save(creatorEmpNo);
    }

    /** 工作空间删除时级联删除整空间角色绑定。 */
    public void deleteAssignmentByWorkspace(String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        List<UserRoleBinding> existing = userRoleBindingRepository.listByWorkspace(workspaceNum);
        for (UserRoleBinding b : existing) {
            UserRoleBinding binding = userRoleBindingFactory.buildBindingByUser(
                    b.getUserId(), workspaceNum);
            if (binding != null) {
                binding.delete(operatorId);
            }
        }
    }

    /** platform_admin 把平台角色赋给某工号（追加式：合并已有平台角色集）。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignPlatformRole(PlatformRoleAssignParam param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getEmpNo(), "empNo 不能为空");
        Assert.notBlank(param.getPlatformRoleNum(), "platformRoleNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.AUTHZ_ASSIGNMENT_LOCK_PREFIX + AuthzConstants.PLATFORM_WORKSPACE_NUM;
        runWithLock(lockKey, () -> {
            UserRoleBinding binding = userRoleBindingFactory.buildPlatformBindingByUser(param.getEmpNo());
            Set<String> merged;
            if (binding == null) {
                merged = new LinkedHashSet<>();
                merged.add(param.getPlatformRoleNum());
                binding = userRoleBindingFactory.buildBinding(
                        RoleBindingType.PLATFORM, param.getEmpNo(),
                        AuthzConstants.PLATFORM_WORKSPACE_NUM, merged);
            } else {
                merged = new LinkedHashSet<>(binding.getRoleNums());
                merged.add(param.getPlatformRoleNum());
                binding.setRoleNums(merged);
            }
            binding.save(operatorId);
            return null;
        });
    }

    /**
     * 覆盖式保存某工号在平台 (SYSTEM workspace) 下的角色集合。
     * <p>roleNums 为空 → delete（解除所有平台角色）；非空 → 覆盖。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserPlatformRoles(String empNo, Set<String> roleNums, String operatorId) {
        Assert.notBlank(empNo, "empNo 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        String lockKey = LockKeyConstant.AUTHZ_ASSIGNMENT_LOCK_PREFIX + AuthzConstants.PLATFORM_WORKSPACE_NUM;
        runWithLock(lockKey, () -> {
            UserRoleBinding existing = userRoleBindingFactory.buildPlatformBindingByUser(empNo);
            if (roleNums == null || roleNums.isEmpty()) {
                if (existing != null) {
                    existing.delete(operatorId);
                }
                return null;
            }
            UserRoleBinding binding = existing == null
                    ? userRoleBindingFactory.buildBinding(
                            RoleBindingType.PLATFORM, empNo,
                            AuthzConstants.PLATFORM_WORKSPACE_NUM, roleNums)
                    : existing;
            if (existing != null) {
                binding.setRoleNums(new LinkedHashSet<>(roleNums));
            }
            binding.save(operatorId);
            return null;
        });
    }

    /** 解除某工号的某平台角色绑定；剩余为空则整条 delete。 */
    @Transactional(rollbackFor = Exception.class)
    public void unassignPlatformRole(String empNo, String platformRoleNum, String operatorId) {
        Assert.notBlank(empNo, "empNo 不能为空");
        Assert.notBlank(platformRoleNum, "platformRoleNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.AUTHZ_ASSIGNMENT_LOCK_PREFIX + AuthzConstants.PLATFORM_WORKSPACE_NUM;
        runWithLock(lockKey, () -> {
            UserRoleBinding binding = userRoleBindingFactory.buildPlatformBindingByUser(empNo);
            if (binding == null) {
                return null;
            }
            Set<String> remained = new LinkedHashSet<>(binding.getRoleNums());
            remained.remove(platformRoleNum);
            if (remained.isEmpty()) {
                binding.delete(operatorId);
            } else {
                binding.setRoleNums(remained);
                binding.save(operatorId);
            }
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 确保用户持有默认平台角色（首次登录时调用）。
     * <p>若该用户在 SYSTEM workspace 下尚无任何角色绑定，则自动分配
     * {@link AuthzConstants#ROLE_PLATFORM_USER}（普通用户）。
     * 已有绑定的用户（无论是 platform_admin 还是自定义平台角色）不受影响。</p>
     *
     * <p>设计原则：幂等——多次登录无副作用；无锁直接检查，因首次并发极少，
     * DB 层 unique key {@code uq_uwr} 已做兜底保护。</p>
     *
     * @param userId 用户 AD 账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultPlatformRole(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        // 已有平台角色绑定 → 跳过
        UserRoleBinding existing = userRoleBindingFactory.buildPlatformBindingByUser(userId);
        if (existing != null && !CollUtil.isEmpty(existing.getRoleNums())) {
            return;
        }
        // 首次登录：分配普通用户默认角色
        Set<String> defaultRoles = new LinkedHashSet<>();
        defaultRoles.add(AuthzConstants.ROLE_PLATFORM_USER);
        UserRoleBinding binding = userRoleBindingFactory.buildBinding(
                RoleBindingType.PLATFORM, userId,
                AuthzConstants.PLATFORM_WORKSPACE_NUM, defaultRoles);
        binding.save("SYSTEM");
        log.info("[AuthzCommandService] ensureDefaultPlatformRole: assigned RL-PLATFORM-USER to userId={}", userId);
    }

    /** 平台级角色允许的资源域（与 AuthzQueryService 对齐） */
    private static final Set<String> PLATFORM_ONLY_DOMAINS = Set.of("workspace", "system");

    private void ensurePermissionsValid(java.util.List<String> codes, RoleScope scope) {
        if (CollUtil.isEmpty(codes)) {
            throw new BusinessException(AuthzErrorCode.PERMISSION_NOT_FOUND.getCode(),
                    AuthzErrorCode.PERMISSION_NOT_FOUND.format("<empty>"));
        }
        for (String code : codes) {
            ink.garry.rd.agent.ws.domain.auth.permission.PermissionMetadata meta = permissionRegistry.findByCode(code);
            if (meta == null) {
                throw new BusinessException(AuthzErrorCode.PERMISSION_NOT_FOUND.getCode(),
                        AuthzErrorCode.PERMISSION_NOT_FOUND.format(code));
            }
            if (scope == null) {
                continue;
            }
            boolean isPlatformDomain = PLATFORM_ONLY_DOMAINS.contains(meta.resourceDomain());
            if (scope == RoleScope.PLATFORM && !isPlatformDomain) {
                throw new BusinessException(AuthzErrorCode.PERMISSION_NOT_FOUND.getCode(),
                        "平台角色不可选择此权限：" + code);
            }
            if (scope == RoleScope.SPACE && isPlatformDomain) {
                throw new BusinessException(AuthzErrorCode.PERMISSION_NOT_FOUND.getCode(),
                        "空间角色不可选择此权限：" + code);
            }
        }
    }

    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "鉴权操作被中断");
        }
        if (!acquired) {
            log.warn("authz command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "鉴权操作正在处理中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
