package ink.garry.rd.agent.ws.application.workspace;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.auth.command.AuthzCommandService;
import ink.garry.rd.agent.ws.application.user.UserQueryService;
import ink.garry.rd.agent.ws.client.auth.constant.AuthzConstants;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.workspace.constant.WorkspaceConstants;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceAssetCountDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceCreateParamDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDeleteParamDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import ink.garry.rd.agent.ws.domain.workspace.factory.WorkspaceFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Workspace 写侧应用服务。
 * <p>
 * 参照 {@code SkillCommandService}：注入 domain {@link WorkspaceFactory} + 读侧
 * {@link WorkspaceQueryService}（CQRS：Command 不直接调 Mapper，唯一性 / 资产计数等读查询走 QueryService）
 * + {@link RedissonClient}（用例级互斥锁）。写方法均标 {@code @Transactional}。
 * <p>
 * <b>方法集</b>：createWorkspace / updateWorkspace / deleteWorkspace —— 与界面「创建 / 编辑 / 删除」
 * 三个操作一一对应；成员的增删与角色调整都在 updateWorkspace 里通过提交完整两栏完成。
 * operatorId 由 adapter 从 UserContextHolder 取出后传入（不在 Param 中显式传）。
 */
@Slf4j
@Service
public class WorkspaceCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s（与仓储层 {@code WorkspaceRepositoryImpl} 统一） */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布的整条用例，超时由 Redisson 自动释放 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private WorkspaceFactory workspaceFactory;
    @Resource
    private WorkspaceQueryService workspaceQueryService;
    @Resource
    private RedissonClient redissonClient;
    /**
     * 鉴权命令服务（跨域）：
     * <ul>
     *   <li>create 末尾绑定创建者为 RL-SPACE-ADMIN</li>
     *   <li>update 末尾按 memberRoles 整空间覆盖写</li>
     *   <li>delete 末尾级联清空整空间用户-角色绑定</li>
     * </ul>
     */
    @Resource
    private AuthzCommandService authzCommandService;
    @Resource
    private UserQueryService userQueryService;

    // ============================================================
    // createWorkspace
    // ============================================================

    /**
     * 创建工作空间（创建人自动入 adminList）。
     *
     * @param param      创建入参（name / description / initialAdminEmpNos / initialMemberEmpNos）
     * @param operatorId 操作人工号（= 创建人）
     * @return 新空间卡片 DTO（含 num，myRole=ADMIN，isCreator=true）
     * @throws BusinessException 参数非法 / 名称冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceDTO createWorkspace(WorkspaceCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateName(param.getName());
        validateDescription(param.getDescription());
        validateMemberCap(param.getInitialAdminEmpNos(), param.getInitialMemberEmpNos(), operatorId);

        // 按 (createNo, name) 抢创建锁：num 尚未生成，防连点 / 重试创建同名空间
        String lockKey = LockKeyConstant.WORKSPACE_CREATE_LOCK_PREFIX + operatorId + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            // 名称唯一性预检（CQRS：走 QueryService）
            if (workspaceQueryService.existsByCreatorAndName(operatorId, param.getName())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在同名空间，请更换");
            }
            Workspace workspace = workspaceFactory.buildWorkspace(
                    param.getName(), param.getDescription(), operatorId,
                    param.getInitialAdminEmpNos(), param.getInitialMemberEmpNos());
            // 领域动作：聚合内统一校验全部不变量并落库 + 发 WORKSPACE_CREATED
            workspace.save(operatorId);
            // 新增：同事务绑定创建者为 RL-SPACE-ADMIN
            authzCommandService.bindCreatorAsSpaceAdmin(workspace.getNum(), operatorId);
            // 按 memberRoles 按角色批量绑人；缺省时从 initialAdminEmpNos / initialMemberEmpNos 自动推导
            if (param.getMemberRoles() != null) {
                // memberRoles 是 roleNum → empNo[]，需反转为 empNo → roleNum[] 后传给 bindUserRoles
                Map<String, Set<String>> normalized = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : param.getMemberRoles().entrySet()) {
                    String roleNum = e.getKey();
                    List<String> empNos = e.getValue();
                    if (empNos == null) continue;
                    for (String empNo : empNos) {
                        if (StrUtil.isBlank(empNo)) continue;
                        normalized.computeIfAbsent(empNo, k -> new LinkedHashSet<>()).add(roleNum);
                    }
                }
                authzCommandService.bindUserRoles(workspace.getNum(), normalized,
                        operatorId, operatorId);
            } else {
                // 从 initialAdminEmpNos / initialMemberEmpNos 自动绑定角色
                Map<String, Set<String>> autoRoles = new LinkedHashMap<>();
                // 创建人已在 bindCreatorAsSpaceAdmin 绑定，此处传入满足 bindUserRoles 的创建人保护检查
                autoRoles.computeIfAbsent(operatorId, k -> new LinkedHashSet<>())
                        .add(AuthzConstants.ROLE_SPACE_ADMIN);
                if (param.getInitialAdminEmpNos() != null) {
                    for (String admin : param.getInitialAdminEmpNos()) {
                        if (StrUtil.isBlank(admin) || operatorId.equals(admin)) continue;
                        autoRoles.computeIfAbsent(admin, k -> new LinkedHashSet<>())
                                .add(AuthzConstants.ROLE_SPACE_ADMIN);
                    }
                }
                if (param.getInitialMemberEmpNos() != null) {
                    for (String member : param.getInitialMemberEmpNos()) {
                        if (StrUtil.isBlank(member)) continue;
                        autoRoles.computeIfAbsent(member, k -> new LinkedHashSet<>())
                                .add(AuthzConstants.ROLE_SPACE_MEMBER);
                    }
                }
                authzCommandService.bindUserRoles(workspace.getNum(), autoRoles,
                        operatorId, operatorId);
            }
            return toCardDTO(workspace, operatorId);
        });
    }

    // ============================================================
    // updateWorkspace
    // ============================================================

    /**
     * 编辑工作空间（整体覆盖：名称 + 描述 + 完整 adminList + 完整 memberList）。
     *
     * @param param      编辑入参（num / name / description / adminEmpNos / memberEmpNos）
     * @param operatorId 操作人工号（必须为该空间管理员）
     * @throws BusinessException 空间不存在 / 非管理员 / 名称冲突 / adminList 为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkspace(WorkspaceUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getNum(), "工作空间业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateName(param.getName());
        validateDescription(param.getDescription());
        // 至少 1 名管理员（聚合内亦会校验，此处提前给出明确错误码）
        if (param.getAdminEmpNos() == null || param.getAdminEmpNos().isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "空间至少保留 1 名管理员");
        }
        validateMemberCap(param.getAdminEmpNos(), param.getMemberEmpNos(), null);

        String lockKey = LockKeyConstant.WORKSPACE_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Workspace workspace = workspaceFactory.buildWorkspaceByNum(param.getNum());
            if (workspace == null) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(), "工作空间不存在 num=" + param.getNum());
            }
            // 仅管理员可编辑
            assertAdmin(workspace, operatorId);
            // 名称有变化时做唯一性预检（按创建人范围）
            if (!StrUtil.equals(workspace.getName(), param.getName())
                    && workspaceQueryService.existsByCreatorAndName(workspace.getCreateNo(), param.getName())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在同名空间，请更换");
            }
            // 整聚合覆盖：set 完整新状态后 save（聚合内统一校验不变量并发 WORKSPACE_UPDATED）
            workspace.setName(param.getName());
            workspace.setDescription(param.getDescription());
            workspace.setAdminList(new ArrayList<>(param.getAdminEmpNos()));
            workspace.setMemberList(param.getMemberEmpNos() == null
                    ? new ArrayList<>() : new ArrayList<>(param.getMemberEmpNos()));
            workspace.save(operatorId);
            // 按 memberRoles 整空间覆盖写；缺省时从 adminEmpNos / memberEmpNos 自动推导
            if (param.getMemberRoles() != null) {
                java.util.Map<String, java.util.Set<String>> normalized = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<String, java.util.List<String>> e : param.getMemberRoles().entrySet()) {
                    normalized.put(e.getKey(),
                            e.getValue() == null ? new java.util.LinkedHashSet<>()
                                    : new java.util.LinkedHashSet<>(e.getValue()));
                }
                authzCommandService.bindUserRoles(param.getNum(), normalized,
                        workspace.getCreateNo(), operatorId);
            } else {
                // 从 adminEmpNos / memberEmpNos 自动绑定角色，确保成员有 RBAC 权限
                Map<String, Set<String>> autoRoles = new LinkedHashMap<>();
                for (String admin : param.getAdminEmpNos()) {
                    if (StrUtil.isBlank(admin)) continue;
                    autoRoles.computeIfAbsent(admin, k -> new LinkedHashSet<>())
                            .add(AuthzConstants.ROLE_SPACE_ADMIN);
                }
                if (param.getMemberEmpNos() != null) {
                    for (String member : param.getMemberEmpNos()) {
                        if (StrUtil.isBlank(member)) continue;
                        autoRoles.computeIfAbsent(member, k -> new LinkedHashSet<>())
                                .add(AuthzConstants.ROLE_SPACE_MEMBER);
                    }
                }
                authzCommandService.bindUserRoles(param.getNum(), autoRoles,
                        workspace.getCreateNo(), operatorId);
            }
            return null;
        });
    }

    // ============================================================
    // deleteWorkspace
    // ============================================================

    /**
     * 软删工作空间（资产非空禁删）。
     *
     * @param param      删除入参（num）
     * @param operatorId 操作人工号（必须为该空间管理员）
     * @throws BusinessException 空间不存在 / 非管理员 / 资产非空
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkspace(WorkspaceDeleteParamDTO param, String operatorId) {
        Assert.notNull(param, "删除参数不能为空");
        Assert.notBlank(param.getNum(), "工作空间业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.WORKSPACE_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Workspace workspace = workspaceFactory.buildWorkspaceByNum(param.getNum());
            if (workspace == null) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(), "工作空间不存在 num=" + param.getNum());
            }
            // 仅管理员可删除
            assertAdmin(workspace, operatorId);
            // 资产非空禁删（CQRS：走 QueryService 计数）
            WorkspaceAssetCountDTO counts = workspaceQueryService.countAssets(param.getNum());
            if (counts.hasAnyAsset()) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "空间内仍有资产，请先清空（agent=" + counts.getAgentCount()
                                + ", skill=" + counts.getSkillCount() + "）");
            }
            // 领域动作：软删（deleted=1）+ 发 WORKSPACE_DELETED
            workspace.delete(operatorId);
            // 新增：级联清空整空间用户-角色绑定
            authzCommandService.deleteAssignmentByWorkspace(param.getNum(), operatorId);
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 校验调用者为空间管理员，否则抛 403（兼容历史 username 成员 ID）。 */
    private void assertAdmin(Workspace workspace, String operatorId) {
        List<String> admins = workspace.getAdminList();
        boolean isAdmin = admins != null && (admins.contains(operatorId)
                || admins.contains(userQueryService.findUsernameByNum(operatorId)));
        if (!isAdmin) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "仅管理员可执行该操作");
        }
    }

    /** 名称长度 [1,64] 校验。 */
    private static void validateName(String name) {
        Assert.notBlank(name, "空间名称不能为空");
        if (name.length() > WorkspaceConstants.NAME_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "空间名称必须在 1~64 字符之间");
        }
    }

    /** 描述长度 ≤200 校验。 */
    private static void validateDescription(String description) {
        if (description != null && description.length() > WorkspaceConstants.DESC_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "空间描述不能超过 200 字符");
        }
    }

    /** 成员总数上限校验（adminList + memberList 合计 ≤ 200；创建场景含创建人）。 */
    private static void validateMemberCap(List<String> admins, List<String> members, String creatorEmpNo) {
        int total = (admins == null ? 0 : admins.size()) + (members == null ? 0 : members.size());
        if (creatorEmpNo != null && (admins == null || !admins.contains(creatorEmpNo))) {
            total += 1;
        }
        if (total > WorkspaceConstants.MEMBER_MAX_TOTAL) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "单空间成员上限为 " + WorkspaceConstants.MEMBER_MAX_TOTAL);
        }
    }

    /** 领域对象 → 卡片 DTO（创建场景：操作人即创建人 + 管理员）。 */
    private static WorkspaceDTO toCardDTO(Workspace w, String operatorId) {
        return WorkspaceDTO.builder()
                .num(w.getNum())
                .name(w.getName())
                .description(w.getDescription())
                .adminCount(w.getAdminList() == null ? 0 : w.getAdminList().size())
                .memberCount(w.getMemberList() == null ? 0 : w.getMemberList().size())
                .myRole(WorkspaceConstants.ROLE_ADMIN)
                .isCreator(operatorId.equals(w.getCreateNo()))
                .createTime(w.getCreateTime())
                .build();
    }

    /**
     * 抢用例级分布式锁后执行编排；样板代码统一收口（参照 {@code SkillCommandService}）。
     *
     * @param lockKey 锁键（已含业务维度后缀）
     * @param action  临界区操作
     * @param <T>     返回类型
     * @return action 返回值
     * @throws BusinessException 抢锁失败（{@link BizCode#CONFLICT}）或线程中断
     */
    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "工作空间操作被中断");
        }
        if (!acquired) {
            log.warn("workspace command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "工作空间正在处理中，请稍后重试");
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
