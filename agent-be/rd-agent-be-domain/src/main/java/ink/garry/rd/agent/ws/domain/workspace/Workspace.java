package ink.garry.rd.agent.ws.domain.workspace;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.workspace.dto.WorkspaceDomainEventDTO;
import ink.garry.rd.agent.ws.domain.workspace.gateway.WorkspaceGateway;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Workspace 聚合根。
 * <p>
 * 资产（Agent / Skill / Tool）的归属容器与多用户协作边界。成员关系以两个工号字符串数组
 * {@link #adminList} / {@link #memberList} 直接内聚（不建独立成员实体 / 角色枚举），
 * 持久化为 workspace 主表的两个 JSON 列。
 * <p>
 * <b>领域动作仅 save / delete 两个</b>：工作空间无版本化 / 发布态等复杂生命周期，「编辑」语义即
 * 「把这个空间的最新完整状态存下来」。因此不设 rename / addMember / promoteToAdmin 等细粒度动作 ——
 * 应用层把前端提交的完整 {@code name + description + adminList + memberList} set 进聚合后调用一次
 * {@link #save(String)}，由 save 统一校验全部不变量并整体覆盖落库。
 * <ul>
 *   <li>创建：由 {@code WorkspaceFactory.create} 装配聚合（工厂职责，非聚合方法），再 save。</li>
 *   <li>权限判定：「是否管理员 / 成员」是应用层鉴权关注点，应用层直接读 {@link #adminList} /
 *       {@link #memberList} 判断，不在聚合上开 isAdmin / containsMember 之类方法。</li>
 * </ul>
 * <p>
 * <b>事件常量</b>：{@link DomainEventConstant#WORKSPACE_CREATED}（首次落库）/
 * {@link DomainEventConstant#WORKSPACE_UPDATED}（已存在编辑落库）/
 * {@link DomainEventConstant#WORKSPACE_DELETED}（逻辑删除）。
 */
@Getter
@Setter
public class Workspace extends DomainEntity {

    /** 空间名称长度上限（与 client.workspace.constant.WorkspaceConstants 保持一致）。 */
    private static final int NAME_MAX_LENGTH = 64;
    /** 空间描述长度上限。 */
    private static final int DESC_MAX_LENGTH = 200;

    // ---- 业务字段 ----

    /** 工作空间业务编号（前缀 WS-，由 {@link WorkspaceGateway#generateWorkspaceNum()} 生成）。 */
    private String num;

    /** 空间名称；同一创建人范围内不重复（由应用层经 ReadGateway 预检 + DB 唯一索引兜底）。 */
    private String name;

    /** 空间描述（可空）。 */
    private String description;

    /** 管理员工号数组；至少保留 1 名。 */
    private List<String> adminList;

    /** 普通成员工号数组；可为空数组。 */
    private List<String> memberList;

    // ---- 装配依赖（由 WorkspaceFactory 在创建时装配） ----

    /** 装配依赖：Workspace 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient WorkspaceRepository workspaceRepository;
    /** 装配依赖：Workspace 网关（业务编号生成）。 */
    private transient WorkspaceGateway workspaceGateway;
    /** 装配依赖：领域事件发布器，由 {@code WorkspaceFactory} 装配。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（infra RepositoryImpl 按 num 重建聚合时用无参 + setter 装配）。 */
    public Workspace() {
    }

    /**
     * 必填字段 + 装配依赖构造方法（由 {@code WorkspaceFactory.buildWorkspace} 创建新聚合时调用）。
     * <p>
     * 仅接收构建聚合所必须的数据字段（name / description / adminList / memberList）与三个装配依赖；
     * 不接收由 save 控制的 num、以及审计字段（createNo / updateNo / createTime / updateTime / deleted），
     * 这些在 {@link #save(String)} 的初始化与赋值阶段统一生成。
     *
     * @param name                空间名称
     * @param description         空间描述（可空）
     * @param adminList           管理员工号数组（创建人已由工厂置入首位）
     * @param memberList          普通成员工号数组
     * @param workspaceRepository Workspace 仓储
     * @param workspaceGateway    Workspace 网关（业务编号生成）
     * @param domainEventPublisher 领域事件发布器
     */
    public Workspace(String name,
                     String description,
                     List<String> adminList,
                     List<String> memberList,
                     WorkspaceRepository workspaceRepository,
                     WorkspaceGateway workspaceGateway,
                     DomainEventPublisher domainEventPublisher) {
        this.name = name;
        this.description = description;
        this.adminList = adminList;
        this.memberList = memberList;
        this.workspaceRepository = workspaceRepository;
        this.workspaceGateway = workspaceGateway;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量校验：名称长度 [1,64]；描述长度 ≤ 200；adminList 非空；
     * 两列表互斥且各自去重（管理员与成员不可同时持有同一工号，列表内不重复）。
     */
    @Override
    public void domainValidate() {
        // 名称长度 [1, 64]
        Assert.notBlank(name, "空间名称必须在 1~64 字符之间");
        Assert.isTrue(name.length() <= NAME_MAX_LENGTH, "空间名称必须在 1~64 字符之间");
        // 描述长度 ≤ 200
        Assert.isTrue(description == null || description.length() <= DESC_MAX_LENGTH,
                "空间描述不能超过 200 字符");
        // 至少 1 名管理员
        Assert.notEmpty(adminList, "空间至少保留 1 名管理员");
        // 各列表内去重：去重后大小不应缩小
        Assert.isTrue(new LinkedHashSet<>(adminList).size() == adminList.size(), "成员工号重复");
        if (memberList != null) {
            Assert.isTrue(new LinkedHashSet<>(memberList).size() == memberList.size(), "成员工号重复");
            // 两列表互斥：同一工号不可同时出现在 adminList 与 memberList
            for (String member : memberList) {
                Assert.isFalse(adminList.contains(member), "成员工号重复");
            }
        }
    }

    /**
     * 保存 / 编辑工作空间（创建与编辑统一入口）。
     * <p>
     * 编辑时由应用层先 set 全部字段（name / description / adminList / memberList）再调用本方法，
     * 整聚合覆盖落库（admin_list / member_list JSON 列整体覆盖写）。
     * <p>
     * 六步顺序：(1) 初始化审计字段 → (2) 暂无前置状态校验 → (3) 赋值（列表初始化 + num 生成）
     * → (4) 领域完整性校验 → (5) 持久化 → (6) 发布事件（新建发 CREATED，已存在发 UPDATED）。
     *
     * @param operatorId 操作人工号
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：save 本身无前置状态约束（无版本 / 发布态生命周期）

        // 3. 赋值：值对象（成员列表）初始化 + num 生成
        if (this.adminList == null) {
            this.adminList = new ArrayList<>();
        }
        if (this.memberList == null) {
            this.memberList = new ArrayList<>();
        }
        // 是否首次落库：在生成 num 之前以主键判定，用于区分 CREATED / UPDATED 事件
        boolean isNew = this.getId() == null;
        if (StrUtil.isBlank(this.num)) {
            this.num = workspaceGateway.generateWorkspaceNum();
        }

        // 4. 领域完整性校验（名称 / 描述长度、adminList 非空、互斥去重）
        this.validate();

        // 5. 持久化（不区分新增 / 更新，统一 upsert 语义；JSON 列整体覆盖写）
        workspaceRepository.save(this);

        // 6. 发布事件：每次 save 必发，按新建 / 编辑区分事件类型（领域事件设计 §3.2.4）
        publishEvent(isNew ? DomainEventConstant.WORKSPACE_CREATED
                : DomainEventConstant.WORKSPACE_UPDATED, operatorId);
    }

    /**
     * 逻辑删除工作空间。
     * <p>
     * 资产非空禁删由应用层调 {@code WorkspaceAssetCountGateway.countAssets} 预检通过后才进入本方法。
     * 六步顺序：(1) 初始化 → (2) 暂无前置校验 → (3) 置 deleted=1 → (4) 完整性校验
     * → (5) 软删（deleteByNum） → (6) 发布 WORKSPACE_DELETED。
     *
     * @param operatorId 操作人工号（兼任删除人，落 update_no / update_time）
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化审计字段（刷新 updateNo / updateTime，兼任删除人 / 删除时间）
        this.initialize(operatorId);

        // 2. 领域规则校验：资产非空禁删由应用层预检，聚合内无额外前置约束

        // 3. 赋值：逻辑删除标识
        this.deleted = 1;

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化删除（infra deleteByNum 实现为软删 UPDATE deleted=1）
        workspaceRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.WORKSPACE_DELETED, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人工号
     */
    private void publishEvent(String type, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(WorkspaceDomainEventDTO.from(this, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
