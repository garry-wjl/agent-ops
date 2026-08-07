package ink.garry.rd.agent.ws.domain.skill;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillDomainEventDTO;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillVersionGatewayDTO;
import ink.garry.rd.agent.ws.domain.skill.gateway.SkillGateway;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillRepository;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Skill 聚合根（v3.0）。
 * <p>
 * 表示一个可被 Agent 调用的能力单元，承载基础元信息、<b>资源文件树</b>与生命周期状态。
 * <p>
 * <b>v3.0 重构（双模式创建 + 文件入库 + 发布检测）</b>：
 * <ul>
 *   <li><b>去对象存储</b>：删除 {@code skillFileKey}（OSS key），改持 {@link #resourceFiles}
 *       整棵文件树（SKILL.md + 资源，内容直接入库）；{@link #domainValidate()} 不再校验文件 key，
 *       改为校验「根 SKILL.md 存在」与「路径合法」。</li>
 *   <li><b>发布检测状态机</b>：在「草稿 → 发布」之间插入 {@link SkillStatus#CHECKING} /
 *       {@link SkillStatus#CHECK_FAILED}；新增 {@link #submitForCheck(String, String)}（DRAFT→CHECKING）、
 *       {@link #markCheckFailed(String)}（CHECKING→CHECK_FAILED）两个动作；{@link #publish(String, String)}
 *       前置条件由 DRAFT 改为 CHECKING（检测通过后由应用层调用）。</li>
 *   <li><b>快照含资源树</b>：{@link #publish} / {@link #rollbackToVersion} 从
 *       {@link SkillGateway#findVersionByNum} 拉取的快照新增 resourceFiles 一项一并覆盖主表。</li>
 * </ul>
 * <p>
 * <b>v2.11 COMPANY 守卫</b>：{@code source = COMPANY} 时禁止用户写入的规则下沉为聚合内不变量。
 * 通过 {@link #assertWritableByLocal()} 暴露断言方法；写动作在六步顺序的"规则校验"阶段内部调用。
 * <p>
 * <b>领域方法</b>：save / submitForCheck / markCheckFailed / publish / delete /
 * rollbackToVersion / unpublish。
 */
@Getter
@Setter
public class Skill extends DomainEntity {

    /** Skill 资源树根文件固定名（大小写敏感）。 */
    private static final String SKILL_MD_FILENAME = "SKILL.md";

    // ---- 业务字段 ----

    /** Skill 业务编号（前缀 SKL，由 {@link SkillGateway#generateSkillNum()} 生成）。 */
    private String num;

    /** Skill 展示名称；同 ownerUserId 下不可重复（仅 SELF；由应用层经 Mapper 校验）。 */
    private String name;

    /** Skill 描述信息（DRAFT 时为草稿描述；PUBLISHED/DEPRECATED 时按业务约定可作为当前版本镜像）。 */
    private String description;

    /** 自由标签数组；列表 facet 聚合筛选用。 */
    private List<String> tags;

    /**
     * Skill 资源文件树（v3.0：替代旧 {@code skillFileKey} 对象存储 key）。
     * <p>
     * 含根 {@code SKILL.md} 与全部附加资源；文本内容 UTF-8、二进制 Base64 直接随节点入库。
     */
    private List<SkillResourceFile> resourceFiles;

    /** Skill 来源（SELF / COMPANY）；COMPANY 写入由 {@link #assertWritableByLocal()} 拦截。 */
    private SkillSource source;

    /** 负责人用户 ID。 */
    private String ownerUserId;

    /** 归属工作空间业务编号（前缀 WS-）；由 SkillFactory 在 create 时从 WorkspaceContextHolder 注入。 */
    private String workspaceNum;

    /** Skill 生命周期状态（DRAFT / CHECKING / CHECK_FAILED / PUBLISHED / DEPRECATED）。 */
    private SkillStatus status;

    /**
     * 当前在线版本号。
     * <p>
     * publish 后指向新发布的版本号；rollbackToVersion 后指向回滚的目标版本号；
     * unpublish 后保留指向最后一次发布的版本号（供历史追溯）。
     */
    private String currentVersionNum;

    // ---- 装配依赖（仅自身仓储 + Skill 网关 + 事件发布器） ----

    /** 装配依赖：Skill 仓储，承担 save / findByNum / deleteByNum 三方法。 */
    private transient SkillRepository skillRepository;
    /** 装配依赖：Skill 网关（业务编号生成 + 版本快照查询）。 */
    private transient SkillGateway skillGateway;
    /** 装配依赖：领域事件发布器，由 {@code SkillFactory} 装配。 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（Lombok 不自动生成；显式声明便于 Mapper / Factory 调用）。 */
    public Skill() {
    }

    // ---- 抽象方法实现 ----

    /**
     * 领域不变量——{@code source = COMPANY} 时禁止本地用户写入。
     * <p>
     * 由 submitForCheck / publish / rollbackToVersion / unpublish 等聚合内写动作在六步顺序的
     * "规则校验"阶段调用；应用层在 setter + save 链（updateSkill / discardDraft）前可显式再调一次。
     * <p>
     * 业务码 1003 对应"公司库 Skill 只读"；客户端可据此提示用户 fork 为自建副本后再编辑。
     *
     * @throws BusinessException 当 {@link #source} == {@link SkillSource#COMPANY} 时
     */
    public void assertWritableByLocal() {
        if (this.source == SkillSource.COMPANY) {
            throw new BusinessException(1003,
                    "公司库 Skill 只读 num=" + this.num);
        }
    }

    /**
     * 领域不变量校验：name / description / source / ownerUserId / status 必填；
     * description 长度上限 5000 字符；tags 长度与数量约束；
     * <b>v3.0</b>：资源树必须含根 {@code SKILL.md}，且所有节点路径合法（无穿越 / 非绝对路径 / 同级不重名）。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(name, "Skill 名称不能为空");
        Assert.notBlank(description, "Skill 描述不能为空");
        Assert.isTrue(description.length() <= 5000, "Skill 描述长度不能超过 5000 字符");
        Assert.notBlank(ownerUserId, "Skill 负责人不能为空");
        Assert.notNull(source, "Skill 来源不能为空");
        Assert.notNull(status, "Skill 状态不能为空");
        if (tags != null) {
            Assert.isTrue(tags.size() <= 20, "Skill 标签数量不能超过 20 个");
            for (String tag : tags) {
                Assert.notBlank(tag, "Skill 标签不能含空白项");
                Assert.isTrue(tag.length() <= 32, "Skill 单个标签长度不能超过 32 字符");
            }
        }
        // v3.0：资源树校验（根 SKILL.md 存在 + 路径合法）
        validateResourceFiles();
    }

    /**
     * 校验资源文件树：必须含根 {@code SKILL.md}（FILE）；所有路径无 {@code ..} 穿越、
     * 非绝对路径；同 parentPath 下 name 不重复。
     *
     * @throws BusinessException 资源树非法时
     */
    private void validateResourceFiles() {
        Assert.notEmpty(resourceFiles, "Skill 资源文件树不能为空");
        boolean hasRootSkillMd = false;
        Set<String> siblingKeys = new HashSet<>();
        for (SkillResourceFile file : resourceFiles) {
            Assert.notNull(file, "Skill 资源节点不能为空");
            Assert.notBlank(file.getPath(), "Skill 资源路径不能为空");
            Assert.notNull(file.getType(), "Skill 资源类型不能为空");
            String path = file.getPath();
            // 路径合法性：无穿越、非绝对路径
            Assert.isTrue(!path.contains(".."), "Skill 资源路径不允许包含 .. ：" + path);
            Assert.isTrue(!path.startsWith("/"), "Skill 资源路径不允许为绝对路径：" + path);
            // 同级不重名（parentPath + name 唯一）
            String siblingKey = (file.getParentPath() == null ? "" : file.getParentPath()) + "/" + file.getName();
            Assert.isTrue(siblingKeys.add(siblingKey), "Skill 资源同级节点重名：" + siblingKey);
            // 根 SKILL.md：parentPath 为空 + path 等于固定名 + 是文件
            if (file.getParentPath() == null
                    && SKILL_MD_FILENAME.equals(path)
                    && file.getType() == SkillResourceFileType.FILE) {
                hasRootSkillMd = true;
            }
        }
        Assert.isTrue(hasRootSkillMd, "Skill 资源树根目录必须包含 " + SKILL_MD_FILENAME);
    }

    /**
     * 保存 / 更新 Skill 元信息（含资源文件树）。
     * <p>
     * 六步顺序：(1) 初始化审计字段 → (2) status 默认 DRAFT → (3) 赋值（值对象初始化 + num 生成）
     * → (4) 领域完整性校验 → (5) 持久化（级联资源树）→ (6) 发布事件。
     *
     * @param operatorId 操作人用户 ID
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化审计字段
        this.initialize(operatorId);

        // 2. 领域规则校验：save 本身无前置状态约束（状态流转交由 submitForCheck / publish / rollback / unpublish）
        if (this.status == null) {
            this.status = SkillStatus.DRAFT;
        }

        // 3. 赋值：值对象初始化 + num 生成
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (this.resourceFiles == null) {
            this.resourceFiles = new ArrayList<>();
        }
        if (StrUtil.isBlank(this.num)) {
            this.num = skillGateway.generateSkillNum();
        }

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（不区分新增/更新，统一 upsert 语义；RepositoryImpl 级联资源树）
        skillRepository.save(this);

        // 6. 发布事件（每次 save 必发，禁止 wasNew 式判断）
        publishEvent(DomainEventConstant.SKILL_SAVED, null, operatorId);
    }

    /**
     * 提交发布：进入检测态（DRAFT → CHECKING）。
     * <p>
     * v3.0 新增。点"发布"后由应用层先调用本方法把状态置 CHECKING（瞬时态，便于检测记录关联），
     * 随后应用层调用 infra 检测工具 {@code SkillChecker} 执行大小 / 格式 / 可用性三检，并据结果调用
     * {@link #publish(String, String)}（通过）或 {@link #markCheckFailed(String)}（不通过）。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 COMPANY 只读 + status==DRAFT + version 非空
     * → (3) status = CHECKING → (4) 完整性校验 → (5) 持久化 → (6) 发布事件。
     *
     * @param version    本次拟发布的版本号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 source==COMPANY，或 status != DRAFT，或 version 为空时
     */
    public void submitForCheck(String version, String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则校验：COMPANY 只读断言 + 仅 DRAFT 可提交检测 + version 非空
        this.assertWritableByLocal();
        Assert.notBlank(version, "发布版本号不能为空");
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.DRAFT,
                "Skill {} 当前状态为 {}，仅草稿态可提交发布检测", num, status);

        // 3. 赋值：进入检测态
        this.status = SkillStatus.CHECKING;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_CHECK_STARTED, version, operatorId);
    }

    /**
     * 标记检测不通过（CHECKING → CHECK_FAILED）。
     * <p>
     * v3.0 新增。由应用层在三检发现问题时调用；用户修复草稿（updateSkill 置回 DRAFT）后可重新发布。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status==CHECKING → (3) status = CHECK_FAILED
     * → (4) 完整性校验 → (5) 持久化 → (6) 发布事件。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != CHECKING 时
     */
    public void markCheckFailed(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则校验：仅检测中可置为检测不通过
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.CHECKING,
                "Skill {} 当前状态为 {}，仅检测中可标记检测不通过", num, status);

        // 3. 赋值
        this.status = SkillStatus.CHECK_FAILED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_CHECK_FAILED, this.currentVersionNum, operatorId);
    }

    /**
     * 发布：检测通过后把指定 version 的 SkillVersion 快照刷新到 Skill 主表（name / description /
     * tags / resourceFiles 4 项），并切版本指针 + 状态置 PUBLISHED。
     * <p>
     * <b>v3.0</b>：前置状态由 DRAFT 改为 {@link SkillStatus#CHECKING}（须先经
     * {@link #submitForCheck(String, String)} + 三检通过）；快照覆盖新增 resourceFiles 一项。
     * 通过 {@link SkillGateway#findVersionByNum} 拉目标版本快照覆盖主表，保证主表与当前在线版本对齐。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status==CHECKING + version 非空 → (3) 拉快照 + 覆盖 4 项
     * + 切 currentVersionNum + status → (4) 完整性校验 → (5) 持久化 → (6) 发布事件。
     *
     * @param version    新版本号字符串（必须已在 SkillVersion 中存在该 skillNum+version 行）
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != CHECKING，或目标 version 在 DB 中不存在 / 已软删时
     */
    public void publish(String version, String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则校验：COMPANY 只读断言；当前状态必须 CHECKING；version 非空
        this.assertWritableByLocal();
        Assert.notBlank(version, "发布版本号不能为空");
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.CHECKING,
                "Skill {} 当前状态为 {}，仅检测中可发布上线", num, status);

        // 3. 赋值
        //   3.1 通过 gateway 拉目标版本快照，覆盖 Skill 主表 4 项（避免主表与版本漂移）
        SkillVersionGatewayDTO snapshot = skillGateway.findVersionByNum(this.num, version);
        // 业务码 1006 对应"资源不存在"；版本必须先存在才能发布
        if (snapshot == null) {
            throw new BusinessException(1006,
                    "Skill " + num + " 不存在版本 " + version + "，无法发布");
        }
        this.name = snapshot.getName();
        this.description = snapshot.getDescription();
        this.tags = snapshot.getTags();
        this.resourceFiles = snapshot.getResourceFiles();
        //   3.2 切版本指针 + 状态
        this.currentVersionNum = version;
        this.status = SkillStatus.PUBLISHED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_VERSION_PUBLISHED, version, operatorId);
    }

    /**
     * 逻辑删除 Skill。
     * <p>
     * 仅允许 {@code status != PUBLISHED} 时删除；PUBLISHED 必须先 {@link #unpublish(String)}。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status == PUBLISHED 时
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status != SkillStatus.PUBLISHED,
                "Skill {} 当前已发布，请先下架后再删除", num);

        // 3. 赋值
        this.deleted = 1;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化删除
        skillRepository.deleteByNum(this.num);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_DELETED, null, operatorId);
    }

    /**
     * 回滚到指定历史版本：把目标 version 的 SkillVersion 快照刷新到 Skill 主表
     * （name / description / tags / resourceFiles 4 项），并切版本指针 + 状态置 PUBLISHED。
     * <p>
     * <b>v3.0</b>：快照覆盖新增 resourceFiles 一项。通过 {@link SkillGateway#findVersionByNum}
     * 拉目标版本快照覆盖主表。版本不存在时直接抛业务异常。
     * <p>
     * 六步顺序：(1) 初始化 → (2) 校验 status != DRAFT + version 非空 → (3) 拉快照 + 覆盖 4 项
     * + 切 currentVersionNum + status = PUBLISHED → (4) 完整性校验 → (5) 持久化 → (6) 发布事件。
     *
     * @param version    目标历史版本号（必须已在 SkillVersion 中存在该 skillNum+version 行）
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status == DRAFT（防覆盖未发布草稿），或目标 version
     *                           在 DB 中不存在 / 已软删时
     */
    public void rollbackToVersion(String version, String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：COMPANY 只读断言 + version 非空 + 非 DRAFT
        this.assertWritableByLocal();
        Assert.notBlank(version, "回滚目标版本号不能为空");
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status != SkillStatus.DRAFT,
                "Skill {} 当前为草稿态，请先放弃草稿后再回滚", num);

        // 3. 赋值
        //   3.1 通过 gateway 拉目标版本快照，覆盖 Skill 主表 4 项（避免主表与版本漂移）
        SkillVersionGatewayDTO snapshot = skillGateway.findVersionByNum(this.num, version);
        // 业务码 1006 对应"资源不存在"；回滚目标版本必须先存在
        if (snapshot == null) {
            throw new BusinessException(1006,
                    "Skill " + num + " 不存在版本 " + version + "，无法回滚");
        }
        this.name = snapshot.getName();
        this.description = snapshot.getDescription();
        this.tags = snapshot.getTags();
        this.resourceFiles = snapshot.getResourceFiles();
        //   3.2 切版本指针 + 状态置回 PUBLISHED（回滚等同于"指向另一个已发布版本"）
        this.currentVersionNum = version;
        this.status = SkillStatus.PUBLISHED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_ROLLED_BACK, version, operatorId);
    }

    /**
     * 下架（unpublish）：将已发布的 Skill 标记为 {@link SkillStatus#DEPRECATED}，
     * 调用方不再可见；{@link #currentVersionNum} 保留供历史追溯。
     *
     * @param operatorId 操作人用户 ID
     * @throws BusinessException 当 status != PUBLISHED 时
     */
    public void unpublish(String operatorId) {
        // 1. 初始化
        this.initialize(operatorId);

        // 2. 领域规则：COMPANY 只读断言 + 仅 PUBLISHED 可下架
        this.assertWritableByLocal();
        Assert.notNull(this.status, "Skill 状态不能为空");
        Assert.isTrue(this.status == SkillStatus.PUBLISHED,
                "Skill {} 当前状态为 {}，仅 PUBLISHED 可下架", num, status);

        // 3. 赋值
        this.status = SkillStatus.DEPRECATED;

        // 4. 完整性校验
        this.validate();

        // 5. 持久化
        skillRepository.save(this);

        // 6. 发布事件
        publishEvent(DomainEventConstant.SKILL_UNPUBLISHED, this.currentVersionNum, operatorId);
    }

    // ---- 私有辅助 ----

    /**
     * 统一封装领域事件发送；未装配 publisher 时直接跳过。
     *
     * @param type       事件类型常量
     * @param version    本次事件涉及的版本号；非版本类事件传 null
     * @param operatorId 操作人用户 ID
     */
    private void publishEvent(String type, String version, String operatorId) {
        if (domainEventPublisher == null) {
            return;
        }
        DomainEventDTO eventDTO = DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(SkillDomainEventDTO.from(this, version, operatorId))
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
        domainEventPublisher.send(eventDTO);
    }
}
