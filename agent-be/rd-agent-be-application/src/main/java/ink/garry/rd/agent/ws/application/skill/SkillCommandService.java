package ink.garry.rd.agent.ws.application.skill;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckErrorDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCreateParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillPublishResultDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceFileDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceTreeDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.SkillVersion;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillCheckResultDTO;
import ink.garry.rd.agent.ws.domain.skill.factory.SkillFactory;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillSource;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillStatus;
import ink.garry.rd.agent.ws.domain.skillcheck.SkillCheckRecord;
import ink.garry.rd.agent.ws.domain.skillcheck.factory.SkillCheckRecordFactory;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.common.util.SkillChecker;
import ink.garry.rd.agent.ws.infra.common.util.SkillResourceCodec;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Skill 写侧应用服务（v3.0）。
 * <p>
 * 编排领域对象方法（{@code Skill} / {@code SkillVersion} / {@code SkillCheckRecord}）+ 通过
 * {@link SkillQueryService} 执行非命令式读查询（CQRS：Command 不直接调 Mapper / Repository / Gateway）。
 * <p>
 * <b>v3.0 变更</b>：
 * <ul>
 *   <li><b>createSkill 双模式落草稿</b>：mode=UPLOAD 调 {@link SkillResourceCodec#expandZip} 解析 zip，
 *       mode=DIRECT 用前端组装的资源树；创建仅落 DRAFT，<b>不再首版发布</b>。</li>
 *   <li><b>publish 三检编排</b>：submitForCheck(→CHECKING) → {@link SkillChecker#check} 三检 →
 *       PASS 则建版本快照 + publish + 落检测记录(PASS)；FAIL 则 markCheckFailed + 落检测记录(FAIL)。
 *       <b>不抛业务异常</b>，返回 {@link SkillPublishResultDTO} 由 adapter 决定响应码。</li>
 *   <li><b>资源树入库</b>：去 skillFileKey；资源以 {@link SkillResourceFile} 文件树随聚合级联存储。</li>
 *   <li><b>检测 / 解压能力</b>：下沉为 infra 工具 {@link SkillChecker} / {@link SkillResourceCodec}，
 *       application 直接静态调用（非领域 Gateway，符合分层约束）。</li>
 *   <li><b>删除 v2.12 独立草稿版本方法</b>（createDraftVersion / updateDraftVersion /
 *       activateDraftVersion / deleteVersion）：其生命周期由「编辑草稿 → publish 过检测」覆盖。</li>
 * </ul>
 * <p>
 * <b>方法集</b>：createSkill / parseZipPreview / updateSkill / discardDraft / publish /
 * rollbackToVersion / unpublish / delete。
 */
@Slf4j
@Service
public class SkillCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s（与仓储层 {@code SkillRepositoryImpl} 统一） */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /**
     * 用例锁租约时长（秒）：30s 比仓储层（10s）更长，
     * 覆盖多步 DB 操作 + 事件发布的整条业务用例；超时由 Redisson 自动释放避免死锁。
     */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    /** 创建方式：上传 zip 压缩包。 */
    private static final String MODE_UPLOAD = "UPLOAD";

    /** 创建方式：直接创建（前端组装资源树）。 */
    private static final String MODE_DIRECT = "DIRECT";

    @Resource
    private SkillFactory skillFactory;
    @Resource
    private SkillCheckRecordFactory skillCheckRecordFactory;
    @Resource
    private SkillQueryService skillQueryService;
    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // createSkill（v3.0：双模式落草稿）
    // ============================================================

    /**
     * 创建自建 Skill（v3.0 双模式，仅落 DRAFT 草稿，不再首版发布）。
     * <ul>
     *   <li>mode=UPLOAD：解压 {@code zipBase64} 切分资源树；</li>
     *   <li>mode=DIRECT：使用 {@code resourceFiles} 前端组装的资源树。</li>
     * </ul>
     *
     * @param param      创建参数；name / description / version / ownerUserId / mode 必填，按 mode 提供 zipBase64 或 resourceFiles
     * @param operatorId 操作人用户 ID
     * @return 创建后的 {@link SkillDTO}（status=DRAFT）
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillDTO createSkill(SkillCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");
        Assert.notBlank(param.getName(), "Skill 名称不能为空");
        Assert.notBlank(param.getDescription(), "Skill 描述不能为空");
        Assert.notBlank(param.getVersion(), "版本号不能为空");
        Assert.notBlank(param.getOwnerUserId(), "Skill 负责人不能为空");
        Assert.notBlank(param.getMode(), "创建方式 mode 不能为空");

        // 按 mode 解析 / 组装资源文件树
        List<SkillResourceFile> resourceFiles = resolveResourceFiles(param);

        // 获取工作空间编号，用于唯一性预检（三元表达式保证 effectively final，可在 lambda 内引用）
        String ws = StrUtil.blankToDefault(
                StrUtil.blankToDefault(param.getWorkspaceNum(), WorkspaceContextHolder.currentWorkspaceNum()),
                "WS-DEFAULT");

        // 锁粒度：(workspaceNum, name) —— createSkill 阶段尚无 Skill num，按空间+名称唯一组合互斥
        String lockKey = LockKeyConstant.SKILL_CREATE_LOCK_PREFIX
                + ws + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            // 1. name 唯一性预检（按空间隔离；经 QueryService；CQRS 约束）
            if (skillQueryService.existsByWorkspaceAndName(ws, param.getName())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "同一工作空间内已存在同名 Skill，请更换名称, workspaceNum=" + ws);
            }

            // 2. 构建并保存 Skill（落 DRAFT；级联资源树）
            Skill skill = skillFactory.buildSkill(
                    param.getName(),
                    param.getDescription(),
                    param.getTags(),
                    resourceFiles,
                    SkillSource.SELF,
                    param.getOwnerUserId());
            skill.setWorkspaceNum(ws);
            skill.save(operatorId);

            log.info("[SkillCommandService] createSkill(draft) ok num={} mode={} operator={}",
                    skill.getNum(), param.getMode(), operatorId);
            return toDTO(skill);
        });
    }

    /**
     * zip 解析预览：解压 zipBase64 返回资源文件树（不落库），供「上传模式」前端预览。
     *
     * @param zipBase64 zip 压缩包的 Base64 串
     * @return 解析出的资源文件树 DTO（skillNum / version 为 null）
     */
    public SkillResourceTreeDTO parseZipPreview(String zipBase64) {
        Assert.notBlank(zipBase64, "zipBase64 不能为空");
        byte[] zipBytes = Base64.decode(zipBase64);
        List<SkillResourceFile> files = SkillResourceCodec.expandZip(zipBytes);
        return SkillResourceTreeDTO.builder()
                .files(files.stream().map(SkillCommandService::resourceToDTO).collect(Collectors.toList()))
                .build();
    }

    // ============================================================
    // updateSkill（v3.0：支持资源树；CHECK_FAILED / PUBLISHED → DRAFT）
    // ============================================================

    /**
     * 更新 Skill 字段（v3.0：支持资源树整树替换）；任何字段变更后 status 自动置 {@code DRAFT}。
     *
     * @param param      更新参数；num 必填，其它字段为空表示不修改
     * @param operatorId 操作人用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSkill(SkillUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "更新参数不能为空");
        Assert.notBlank(param.getNum(), "Skill 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + param.getNum(), () -> {
            Skill skill = skillFactory.buildSkillByNum(param.getNum());
            // setter+save 链不会经过聚合内写动作的"规则校验"步骤，应用层先调断言显式拦截
            skill.assertWritableByLocal();

            // 覆盖字段（非空才覆盖；显式传空集合的 tags 视为清空）
            if (StrUtil.isNotBlank(param.getName())) {
                skill.setName(param.getName());
            }
            if (StrUtil.isNotBlank(param.getDescription())) {
                skill.setDescription(param.getDescription());
            }
            if (param.getTags() != null) {
                skill.setTags(new ArrayList<>(param.getTags()));
            }
            if (param.getResourceFiles() != null) {
                skill.setResourceFiles(toResourceFiles(param.getResourceFiles()));
            }
            // 任何字段变更都置 DRAFT（含 CHECK_FAILED 修复后重发场景），等待用户确认后再 publish
            skill.setStatus(SkillStatus.DRAFT);

            skill.save(operatorId);
            log.info("[SkillCommandService] updateSkill ok num={} operator={}", skill.getNum(), operatorId);
        });
    }

    // ============================================================
    // discardDraft
    // ============================================================

    /**
     * 放弃草稿态修改：用当前在线版本快照覆盖回 Skill（含资源树），状态切回 {@code PUBLISHED}。
     * <p>版本快照通过 {@code SkillFactory.buildSkillVersionByNum} 加载（含 resourceFiles）。
     *
     * @param skillNum   Skill 业务编号
     * @param operatorId 操作人用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(String skillNum, String operatorId) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + skillNum, () -> {
            Skill skill = skillFactory.buildSkillByNum(skillNum);
            skill.assertWritableByLocal();
            Assert.isTrue(skill.getStatus() == SkillStatus.DRAFT,
                    "Skill {} 当前不在草稿态，无需放弃", skillNum);
            Assert.notBlank(skill.getCurrentVersionNum(),
                    "Skill {} 尚未发布过任何版本，无法回滚草稿", skillNum);

            // 加载当前版本号对应的版本 num（CQRS：经 QueryService）
            String versionNum = skillQueryService.findVersionNum(skillNum, skill.getCurrentVersionNum());
            if (versionNum == null) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                        "Skill " + skillNum + " 当前版本 " + skill.getCurrentVersionNum() + " 不存在");
            }
            // 经 Factory 加载版本聚合（含资源树快照）
            SkillVersion current = skillFactory.buildSkillVersionByNum(versionNum);

            // 覆盖回 Skill 主表
            skill.setName(current.getName());
            skill.setDescription(current.getDescription());
            skill.setTags(current.getTags() == null ? new ArrayList<>() : new ArrayList<>(current.getTags()));
            skill.setResourceFiles(current.getResourceFiles() == null
                    ? new ArrayList<>() : new ArrayList<>(current.getResourceFiles()));
            skill.setStatus(SkillStatus.PUBLISHED);

            skill.save(operatorId);
            log.info("[SkillCommandService] discardDraft ok num={} restoredVersion={} operator={}",
                    skillNum, current.getVersion(), operatorId);
        });
    }

    // ============================================================
    // publish（v3.0：检测中 → 三检 → 生效 / 检测不通过）
    // ============================================================

    /**
     * 发布 Skill（v3.0 三检编排，不抛业务异常）。
     * <p>
     * 编排：submitForCheck(DRAFT→CHECKING) → {@link SkillChecker#check} 大小/格式/可用性三检 →
     * <ul>
     *   <li>PASS：建 SkillVersion 快照（含资源树）→ version.save+publish → skill.publish(CHECKING→PUBLISHED)
     *       → 落检测记录(PASS)；</li>
     *   <li>FAIL：skill.markCheckFailed(CHECKING→CHECK_FAILED) → 落检测记录(FAIL)。</li>
     * </ul>
     * 版本号唯一冲突仍抛 {@link BizCode#VERSION_CONFLICT}（参数错误，非检测结果）。
     *
     * @param skillNum   Skill 业务编号
     * @param version    本次发布版本号
     * @param operatorId 操作人用户 ID
     * @return 发布结果（result=PASS/FAIL + 三项子结果 + errors + 检测记录 num）
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillPublishResultDTO publish(String skillNum, String version, String operatorId) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(version, "版本号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        return runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + skillNum, () -> {
            Skill skill = skillFactory.buildSkillByNum(skillNum);
            if (skill == null) {
                throw new BusinessException(BizCode.SKILL_NOT_FOUND.getCode(), "Skill 不存在 num=" + skillNum);
            }

            // 1. 版本号唯一性预检（经 QueryService；版本冲突属参数错误，抛异常）
            if (skillQueryService.existsByVersion(skillNum, version)) {
                throw new BusinessException(BizCode.VERSION_CONFLICT.getCode(),
                        "Skill " + skillNum + " 版本号 " + version + " 已存在");
            }

            // 2. 提交发布：DRAFT → CHECKING（COMPANY 守卫由聚合内断言）
            skill.submitForCheck(version, operatorId);

            // 3. 三检（infra 工具，不抛异常返回结构体）
            SkillCheckResultDTO checkResult = SkillChecker.check(skill.getResourceFiles());

            String checkRecordNum;
            if (checkResult.getResult() == SkillCheckResult.PASS) {
                // 4a. 通过：建版本快照（含资源树）→ save → publish；再切 Skill 主表
                SkillVersion newVersion = skillFactory.buildSkillVersion(
                        skill.getNum(), version, skill.getName(), skill.getDescription(),
                        skill.getTags(), skill.getResourceFiles());
                newVersion.save(operatorId);
                newVersion.publish(operatorId);
                skill.publish(version, operatorId);
                checkRecordNum = saveCheckRecord(skill, version, checkResult, operatorId);
                log.info("[SkillCommandService] publish PASS num={} version={} operator={}",
                        skillNum, version, operatorId);
            } else {
                // 4b. 不通过：CHECKING → CHECK_FAILED；落检测记录
                skill.markCheckFailed(operatorId);
                checkRecordNum = saveCheckRecord(skill, version, checkResult, operatorId);
                log.info("[SkillCommandService] publish CHECK_FAILED num={} version={} errorCount={}",
                        skillNum, version, checkResult.getErrors() == null ? 0 : checkResult.getErrors().size());
            }
            return toPublishResultDTO(checkResult, checkRecordNum, version);
        });
    }

    /** 落一条检测记录，返回其业务编号。 */
    private String saveCheckRecord(Skill skill, String version, SkillCheckResultDTO checkResult, String operatorId) {
        SkillCheckRecord record = skillCheckRecordFactory.buildCheckRecord(
                skill.getNum(), version,
                checkResult.getResult(),
                checkResult.getSizeResult(),
                checkResult.getFormatResult(),
                checkResult.getAvailabilityResult(),
                checkResult.getErrors() == null ? new ArrayList<>() : new ArrayList<>(checkResult.getErrors()),
                checkResult.getCostMs(),
                skill.getWorkspaceNum());
        record.save(operatorId);
        return record.getNum();
    }

    // ============================================================
    // rollbackToVersion
    // ============================================================

    /**
     * 回滚到指定历史版本（切 {@code Skill.currentVersionNum} + 覆盖主表快照 + status=PUBLISHED）。
     *
     * @param skillNum      Skill 业务编号
     * @param targetVersion 目标历史版本号
     * @param operatorId    操作人用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackToVersion(String skillNum, String targetVersion, String operatorId) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(targetVersion, "目标版本号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");

        runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + skillNum, () -> {
            Skill skill = skillFactory.buildSkillByNum(skillNum);
            // 目标版本存在性预检（经 QueryService；CQRS 约束）
            if (!skillQueryService.existsByVersion(skillNum, targetVersion)) {
                throw new BusinessException(BizCode.NOT_FOUND.getCode(),
                        "Skill " + skillNum + " 不存在版本 " + targetVersion);
            }
            skill.rollbackToVersion(targetVersion, operatorId);
            log.info("[SkillCommandService] rollbackToVersion ok num={} target={} operator={}",
                    skillNum, targetVersion, operatorId);
        });
    }

    // ============================================================
    // unpublish / delete
    // ============================================================

    /** 下架（PUBLISHED → DEPRECATED）。COMPANY 守卫由 {@link Skill#unpublish} 内部兜底。 */
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(String skillNum, String operatorId) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");
        runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + skillNum, () -> {
            Skill skill = skillFactory.buildSkillByNum(skillNum);
            skill.unpublish(operatorId);
            log.info("[SkillCommandService] unpublish ok num={} operator={}", skillNum, operatorId);
        });
    }

    /** 逻辑删除（仅 status != PUBLISHED）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String skillNum, String operatorId) {
        Assert.notBlank(skillNum, "Skill 业务编号不能为空");
        Assert.notBlank(operatorId, "操作人不能为空");
        runWithLock(LockKeyConstant.SKILL_COMMAND_LOCK_PREFIX + skillNum, () -> {
            Skill skill = skillFactory.buildSkillByNum(skillNum);
            skill.delete(operatorId);
            log.info("[SkillCommandService] delete ok num={} operator={}", skillNum, operatorId);
        });
    }

    // ============================================================
    // helpers — 资源树映射 / DTO 转换
    // ============================================================

    /** 按创建方式解析 / 组装资源文件树。 */
    private List<SkillResourceFile> resolveResourceFiles(SkillCreateParamDTO param) {
        if (MODE_UPLOAD.equalsIgnoreCase(param.getMode())) {
            Assert.notBlank(param.getZipBase64(), "上传模式下 zipBase64 不能为空");
            return SkillResourceCodec.expandZip(Base64.decode(param.getZipBase64()));
        } else if (MODE_DIRECT.equalsIgnoreCase(param.getMode())) {
            Assert.notEmpty(param.getResourceFiles(), "直接创建模式下资源文件树不能为空");
            return toResourceFiles(param.getResourceFiles());
        }
        throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                "不支持的创建方式 mode=" + param.getMode());
    }

    /** client 资源 DTO 列表 → domain 资源值对象列表。 */
    private static List<SkillResourceFile> toResourceFiles(List<SkillResourceFileDTO> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        return dtos.stream().map(d -> SkillResourceFile.builder()
                .path(d.getPath())
                .type(d.getType() == null ? null : SkillResourceFileType.valueOf(d.getType()))
                .name(d.getName())
                .parentPath(d.getParentPath())
                .encoding(d.getEncoding())
                .mime(d.getMime())
                .content(d.getContent())
                .build()).collect(Collectors.toList());
    }

    /** domain 资源值对象 → client 资源 DTO。 */
    private static SkillResourceFileDTO resourceToDTO(SkillResourceFile vo) {
        return SkillResourceFileDTO.builder()
                .path(vo.getPath())
                .type(vo.getType() == null ? null : vo.getType().name())
                .name(vo.getName())
                .parentPath(vo.getParentPath())
                .encoding(vo.getEncoding())
                .mime(vo.getMime())
                .content(vo.getContent())
                .build();
    }

    /** 检测结果 → 发布结果 DTO。 */
    private static SkillPublishResultDTO toPublishResultDTO(SkillCheckResultDTO checkResult,
                                                            String checkRecordNum, String version) {
        List<SkillCheckErrorDTO> errors = (checkResult.getErrors() == null)
                ? new ArrayList<>()
                : checkResult.getErrors().stream().map(SkillCommandService::errorToDTO).collect(Collectors.toList());
        return SkillPublishResultDTO.builder()
                .result(checkResult.getResult() == null ? null : checkResult.getResult().name())
                .sizeResult(checkResult.getSizeResult() == null ? null : checkResult.getSizeResult().name())
                .formatResult(checkResult.getFormatResult() == null ? null : checkResult.getFormatResult().name())
                .availabilityResult(checkResult.getAvailabilityResult() == null ? null : checkResult.getAvailabilityResult().name())
                .errors(errors)
                .checkRecordNum(checkRecordNum)
                .version(version)
                .build();
    }

    /** 检测错误值对象 → client DTO。 */
    private static SkillCheckErrorDTO errorToDTO(SkillCheckError e) {
        return SkillCheckErrorDTO.builder()
                .checkItem(e.getCheckItem() == null ? null : e.getCheckItem().name())
                .location(e.getLocation())
                .message(e.getMessage())
                .build();
    }

    /** Skill 领域对象 → {@link SkillDTO}。 */
    private static SkillDTO toDTO(Skill skill) {
        return SkillDTO.builder()
                .num(skill.getNum())
                .name(skill.getName())
                .description(skill.getDescription())
                .tags(skill.getTags())
                .source(skill.getSource() == null ? null : skill.getSource().name())
                .ownerUserId(skill.getOwnerUserId())
                .status(skill.getStatus() == null ? null : skill.getStatus().name())
                .currentVersionNum(skill.getCurrentVersionNum())
                .createTime(skill.getCreateTime())
                .updateTime(skill.getUpdateTime())
                .build();
    }

    // ============================================================
    // 分布式锁 helper
    // ============================================================

    /**
     * 以给定 key 抢分布式锁后执行带返回值的用例。
     *
     * @param key    完整锁 key（已拼好前缀 + 业务 ID）
     * @param action 临界区操作
     * @param <T>    返回值类型
     * @return action 的返回值
     * @throws BusinessException 抢锁失败或线程中断（{@link BizCode#CONFLICT}）
     */
    private <T> T runWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Skill 用例编排被中断");
        }
        if (!acquired) {
            log.warn("skill command lock busy key={}", key);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Skill 正在保存中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * {@link #runWithLock(String, Supplier)} 的无返回值重载，便于 void 写方法。
     *
     * @param key    完整锁 key
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithLock(String key, Runnable action) {
        runWithLock(key, () -> {
            action.run();
            return null;
        });
    }
}
